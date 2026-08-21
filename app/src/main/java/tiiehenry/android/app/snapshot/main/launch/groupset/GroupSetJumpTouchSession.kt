package tiiehenry.android.app.snapshot.main.launch.groupset

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SnapshotViewModel
import tiiehenry.android.app.snapshot.group.SnapGroupSet
import tiiehenry.android.app.snapshot.main.launch.ArchiveListItem
import kotlin.math.abs

/**
 * 存档 Tab 长按快跳：超时后弹出分组集菜单；支持松手点选与按住拖选。
 *
 * 协议见 docs/systems/snapshot/GROUP_SET.md「交互协议（参考 Singular）」：
 * 锚点 OnTouchListener 消费整次 pointer，用 rawX/rawY 做 hover/commit。
 */
class GroupSetJumpTouchSession(
    private val anchor: View,
    private val snapshotViewModel: SnapshotViewModel,
    private val onSelectSet: (SnapGroupSet) -> Unit,
    private val onShortClick: () -> Unit,
) : View.OnTouchListener {

    private val handler = Handler(Looper.getMainLooper())
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val touchSlop = ViewConfiguration.get(anchor.context).scaledTouchSlop
    private val touchSlopSquared = touchSlop * touchSlop

    private var downRawX = 0f
    private var downRawY = 0f
    private var downX = 0f
    private var downY = 0f
    private var pendingShow = false
    private var cancelled = false
    private var pickerMode = false
    private var popupHandle: GroupSetJumpPopup.Handle? = null
    private var pressFocusGuard: ViewTreeObserver.OnWindowFocusChangeListener? = null

    private val showRunnable = Runnable {
        if (!pendingShow || cancelled) return@Runnable
        pendingShow = false
        showPopup()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (popupShowing()) {
                    updatePressState(event, pressed = true)
                    dismissPopup()
                    pickerMode = false
                    anchor.parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
                downRawX = event.rawX
                downRawY = event.rawY
                downX = event.x
                downY = event.y
                pendingShow = true
                cancelled = false
                pickerMode = false
                updatePressState(event, pressed = true)
                handler.postDelayed(showRunnable, longPressTimeout)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pendingShow) {
                    if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) {
                        cancelPending()
                        updatePressState(event, pressed = false)
                    }
                    return true
                }
                val handle = popupHandle ?: return true
                if (!pickerMode) {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (dx * dx + dy * dy > touchSlopSquared) {
                        pickerMode = true
                        anchor.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
                if (pickerMode) {
                    handle.updateHover(event.rawX, event.rawY)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (pendingShow) {
                    cancelPending()
                    updatePressState(event, pressed = false)
                    onShortClick()
                    resetSessionFlags()
                    return true
                }
                val handle = popupHandle
                val wasPickerMode = pickerMode
                pickerMode = false
                if (handle == null) {
                    updatePressState(event, pressed = false)
                    clearPressFocusGuard()
                    anchor.parent?.requestDisallowInterceptTouchEvent(false)
                    resetSessionFlags()
                    return true
                }
                if (wasPickerMode) {
                    anchor.parent?.requestDisallowInterceptTouchEvent(false)
                    val selected = handle.commitSelection(event.rawX, event.rawY)
                    dismissPopup()
                    if (selected != null) {
                        onSelectSet(selected)
                    }
                } else {
                    // Tap 路径：菜单保持，供松手后点行
                    keepPressedWhileShowing()
                    anchor.parent?.requestDisallowInterceptTouchEvent(false)
                }
                resetSessionFlags()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelPending()
                updatePressState(event, pressed = false)
                dismissPopup()
                pickerMode = false
                anchor.parent?.requestDisallowInterceptTouchEvent(false)
                resetSessionFlags()
                return true
            }
        }
        return false
    }

    private fun showPopup() {
        val items = snapshotViewModel.archiveList.value.orEmpty()
            .filterIsInstance<ArchiveListItem.SetHeader>()
            .map { GroupSetJumpPopup.JumpItem(it.set, it.groupCount) }
        if (items.isEmpty()) {
            updatePressState(null, pressed = false)
            return
        }
        val handle = GroupSetJumpPopup.create(
            anchor = anchor,
            items = items,
            onItemClick = { set ->
                onSelectSet(set)
            },
        ) ?: run {
            updatePressState(null, pressed = false)
            return
        }
        popupHandle = handle
        handle.setOnDismissListener {
            if (popupHandle === handle) {
                popupHandle = null
                clearPressFocusGuard()
                anchor.isPressed = false
            }
        }
        anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        handle.show()
        installPressFocusGuard()
        keepPressedWhileShowing()
        anchor.parent?.requestDisallowInterceptTouchEvent(true)
        handle.updateHover(downRawX, downRawY)
    }

    private fun popupShowing(): Boolean = popupHandle?.isShowing == true

    private fun dismissPopup() {
        popupHandle?.dismiss()
        popupHandle = null
        clearPressFocusGuard()
        anchor.isPressed = false
    }

    private fun cancelPending() {
        pendingShow = false
        cancelled = true
        handler.removeCallbacks(showRunnable)
    }

    private fun resetSessionFlags() {
        pendingShow = false
        cancelled = false
        handler.removeCallbacks(showRunnable)
    }

    private fun updatePressState(event: MotionEvent?, pressed: Boolean) {
        if (pressed && event != null) {
            anchor.drawableHotspotChanged(event.x, event.y)
        }
        anchor.isPressed = pressed
    }

    private fun keepPressedWhileShowing() {
        if (!popupShowing()) return
        anchor.isPressed = true
        anchor.post {
            if (popupShowing()) {
                anchor.isPressed = true
            }
        }
    }

    private fun installPressFocusGuard() {
        clearPressFocusGuard()
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (!hasFocus && popupShowing()) {
                anchor.isPressed = true
            }
        }
        pressFocusGuard = listener
        anchor.viewTreeObserver.addOnWindowFocusChangeListener(listener)
    }

    private fun clearPressFocusGuard() {
        val listener = pressFocusGuard ?: return
        val observer = anchor.viewTreeObserver
        if (observer.isAlive) {
            observer.removeOnWindowFocusChangeListener(listener)
        }
        pressFocusGuard = null
    }

    companion object {
        @SuppressLint("ClickableViewAccessibility")
        fun attach(
            tab: View,
            snapshotViewModel: SnapshotViewModel,
            onSelectSet: (SnapGroupSet) -> Unit,
            onShortClick: () -> Unit,
        ) {
            tab.contentDescription =
                tab.context.getString(R.string.group_set_jump_content_description)
            tab.setOnClickListener(null)
            tab.setOnTouchListener(
                GroupSetJumpTouchSession(tab, snapshotViewModel, onSelectSet, onShortClick)
            )
        }
    }
}

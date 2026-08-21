package tiiehenry.android.app.snapshot.main.launch.groupset

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.PopupMenu
import tiiehenry.android.app.snapshot.SnapshotViewModel
import tiiehenry.android.app.snapshot.group.SnapGroupSet
import kotlin.math.abs

/**
 * 存档 Tab 长按快跳：超时后弹出分组集菜单；超时前 slop/CANCEL 取消，不 performClick。
 * 短按仍由外层 click 切 Tab。
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

    private var downX = 0f
    private var downY = 0f
    private var pendingShow = false
    private var shown = false
    private var cancelled = false

    private val showRunnable = Runnable {
        if (!pendingShow || cancelled) return@Runnable
        pendingShow = false
        shown = true
        showPopup()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                pendingShow = true
                shown = false
                cancelled = false
                handler.postDelayed(showRunnable, longPressTimeout)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pendingShow &&
                    (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop)
                ) {
                    cancelPending()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (pendingShow && !shown) {
                    cancelPending()
                    onShortClick()
                }
                reset()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelPending()
                reset()
                return true
            }
        }
        return false
    }

    private fun cancelPending() {
        pendingShow = false
        cancelled = true
        handler.removeCallbacks(showRunnable)
    }

    private fun reset() {
        pendingShow = false
        shown = false
        handler.removeCallbacks(showRunnable)
    }

    private fun showPopup() {
        val sets = snapshotViewModel.groupSetList.value.orEmpty()
        if (sets.isEmpty()) {
            shown = false
            return
        }
        PopupMenu(anchor.context, anchor).apply {
            sets.forEachIndexed { index, set ->
                menu.add(0, index, index, set.name)
            }
            setOnMenuItemClickListener { item ->
                val set = sets.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener false
                onSelectSet(set)
                true
            }
            setOnDismissListener { shown = false }
            show()
        }
    }

    companion object {
        @SuppressLint("ClickableViewAccessibility")
        fun attach(
            tab: View,
            snapshotViewModel: SnapshotViewModel,
            onSelectSet: (SnapGroupSet) -> Unit,
            onShortClick: () -> Unit,
        ) {
            tab.setOnClickListener(null)
            tab.setOnTouchListener(
                GroupSetJumpTouchSession(tab, snapshotViewModel, onSelectSet, onShortClick)
            )
        }
    }
}

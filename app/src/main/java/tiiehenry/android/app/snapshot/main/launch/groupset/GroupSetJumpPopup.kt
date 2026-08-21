package tiiehenry.android.app.snapshot.main.launch.groupset

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.group.SnapGroupSet

/**
 * 底栏长按快跳菜单：自定义 [PopupWindow]，支持锚点侧 [updateHover] / [commitSelection]。
 */
class GroupSetJumpPopup private constructor(
    private val anchor: View,
    private val popup: PopupWindow,
    private val recyclerView: RecyclerView,
    private val adapter: RowAdapter,
) {

    data class JumpItem(
        val set: SnapGroupSet,
        val groupCount: Int,
    )

    class Handle internal constructor(
        private val popup: GroupSetJumpPopup,
    ) {
        val isShowing: Boolean get() = popup.popup.isShowing

        fun show() = popup.show()

        fun updateHover(rawX: Float, rawY: Float) {
            popup.adapter.hoveredPosition = popup.findPosition(rawX, rawY)
        }

        fun commitSelection(rawX: Float, rawY: Float): SnapGroupSet? {
            val position = popup.findPosition(rawX, rawY)
            popup.adapter.hoveredPosition = RecyclerView.NO_POSITION
            if (position == RecyclerView.NO_POSITION) return null
            return popup.adapter.itemAt(position)?.set
        }

        fun dismiss() {
            popup.adapter.hoveredPosition = RecyclerView.NO_POSITION
            popup.popup.dismiss()
        }

        fun setOnDismissListener(listener: () -> Unit) {
            popup.popup.setOnDismissListener { listener() }
        }
    }

    private fun show() {
        val content = popup.contentView
        val displayMetrics = anchor.resources.displayMetrics
        val maxHeight = (displayMetrics.heightPixels * 0.4f).toInt()
        val minWidth = (200 * displayMetrics.density).toInt()
        val maxWidth = (displayMetrics.widthPixels * 0.72f).toInt()
        val width = minWidth.coerceAtMost(maxWidth)

        // 先定宽再量高，避免 RecyclerView 子项 match_parent 量出 0 高
        content.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST),
        )
        val height = content.measuredHeight.coerceIn(
            (44 * displayMetrics.density).toInt(),
            maxHeight,
        )
        popup.width = width
        popup.height = height

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val x = location[0]
        val y = (location[1] - height).coerceAtLeast(0)
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
    }

    private fun findPosition(rawX: Float, rawY: Float): Int {
        if (!popup.isShowing) return RecyclerView.NO_POSITION
        val local = IntArray(2)
        recyclerView.getLocationOnScreen(local)
        val x = rawX - local[0]
        val y = rawY - local[1]
        if (x < 0f || y < 0f || x > recyclerView.width || y > recyclerView.height) {
            return RecyclerView.NO_POSITION
        }
        val child = recyclerView.findChildViewUnder(x, y) ?: return RecyclerView.NO_POSITION
        return recyclerView.getChildAdapterPosition(child)
    }

    private class RowAdapter(
        private val items: List<JumpItem>,
        private val onItemClick: (SnapGroupSet) -> Unit,
        private val onDismiss: () -> Unit,
    ) : RecyclerView.Adapter<RowAdapter.RowHolder>() {

        var hoveredPosition: Int = RecyclerView.NO_POSITION
            set(value) {
                val old = field
                field = value
                if (old != RecyclerView.NO_POSITION && old != value) notifyItemChanged(old)
                if (value != RecyclerView.NO_POSITION && value != old) notifyItemChanged(value)
            }

        fun itemAt(position: Int): JumpItem? = items.getOrNull(position)

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_group_set_jump, parent, false)
            return RowHolder(view)
        }

        override fun onBindViewHolder(holder: RowHolder, position: Int) {
            val item = items[position]
            holder.title.text = item.set.name
            holder.count.text = holder.itemView.context.getString(
                R.string.group_set_count_format,
                item.groupCount,
            )
            holder.itemView.isSelected = position == hoveredPosition
            holder.itemView.setOnClickListener {
                onItemClick(item.set)
                onDismiss()
            }
        }

        class RowHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val title: TextView = itemView.findViewById(R.id.jump_title)
            val count: TextView = itemView.findViewById(R.id.jump_count)
        }
    }

    companion object {
        fun create(
            anchor: View,
            items: List<JumpItem>,
            onItemClick: (SnapGroupSet) -> Unit,
        ): Handle? {
            if (items.isEmpty()) return null
            val context = anchor.context
            val content = LayoutInflater.from(context)
                .inflate(R.layout.popup_group_set_jump, null, false)
            val recyclerView = content.findViewById<RecyclerView>(R.id.jump_recycler)

            lateinit var popup: PopupWindow
            val adapter = RowAdapter(
                items = items,
                onItemClick = onItemClick,
                onDismiss = { popup.dismiss() },
            )
            recyclerView.layoutManager = LinearLayoutManager(context)
            recyclerView.adapter = adapter
            recyclerView.isNestedScrollingEnabled = false

            popup = PopupWindow(
                content,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true,
            ).apply {
                elevation = 8f * context.resources.displayMetrics.density
                isOutsideTouchable = true
                isFocusable = true
            }

            return Handle(
                GroupSetJumpPopup(anchor, popup, recyclerView, adapter)
            )
        }
    }
}

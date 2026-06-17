package tiiehenry.android.app.snapshot.main.timeline

import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import tiiehenry.android.app.snapshot.databinding.ItemTimelineDateHeaderBinding

class TimelineStickyHeaderDecoration(
    private val adapter: TimelineAdapter
) : RecyclerView.ItemDecoration() {

    private var stickyHeaderView: View? = null
    private var stickyBinding: ItemTimelineDateHeaderBinding? = null

    override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val childCount = parent.childCount
        if (childCount == 0) return

        val topChild = parent.getChildAt(0)
        val topPosition = parent.getChildAdapterPosition(topChild)
        if (topPosition == RecyclerView.NO_POSITION) return

        val headerPosition = findHeaderPosition(topPosition)
        if (headerPosition == RecyclerView.NO_POSITION) return

        val headerItem = adapter.currentList.getOrNull(headerPosition) as? TimelineListItem.DateHeader
        if (headerItem == null) return

        val headerView = getOrCreateHeaderView(parent)
        stickyBinding?.dateHeaderLabel?.text = headerItem.label

        val widthSpec = View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        headerView.measure(widthSpec, heightSpec)
        val headerHeight = headerView.measuredHeight

        var translationY = 0f
        val nextHeaderPosition = findNextHeaderPosition(headerPosition)
        if (nextHeaderPosition != RecyclerView.NO_POSITION) {
            val nextHeaderChild = parent.findViewHolderForAdapterPosition(nextHeaderPosition)?.itemView
            if (nextHeaderChild != null) {
                val offset = nextHeaderChild.top - headerHeight
                if (offset < 0) {
                    translationY = offset.toFloat()
                }
            }
        }

        canvas.save()
        canvas.translate(0f, translationY)
        headerView.layout(0, 0, parent.width, headerHeight)
        headerView.draw(canvas)
        canvas.restore()
    }

    private fun findHeaderPosition(position: Int): Int {
        for (i in position downTo 0) {
            if (adapter.isDateHeader(i)) return i
        }
        return RecyclerView.NO_POSITION
    }

    private fun findNextHeaderPosition(currentHeaderPosition: Int): Int {
        for (i in currentHeaderPosition + 1 until adapter.itemCount) {
            if (adapter.isDateHeader(i)) return i
        }
        return RecyclerView.NO_POSITION
    }

    private fun getOrCreateHeaderView(parent: RecyclerView): View {
        if (stickyHeaderView == null) {
            val binding = ItemTimelineDateHeaderBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            stickyBinding = binding
            stickyHeaderView = binding.root
        }
        return stickyHeaderView!!
    }
}

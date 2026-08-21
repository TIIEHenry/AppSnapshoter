package tiiehenry.android.app.snapshot.main.launch.groupset

import android.view.View
import android.view.ViewOutlineProvider
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.fragment.app.FragmentManager
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SnapshotViewModel
import tiiehenry.android.app.snapshot.databinding.ItemGroupSetBinding
import tiiehenry.android.app.snapshot.main.launch.ArchiveListItem
import tiiehenry.android.app.snapshot.main.launch.GroupsAdapter
import kotlin.math.min

/**
 * 存档列表分组集 Header 吸顶：真实 overlay，折展/刷新/设置可点。
 * 下一块（下一集 Header 或独立分组）顶上来时把当前条推走。
 */
class GroupSetStickyHeader(
    private val recyclerView: RecyclerView,
    private val overlay: ItemGroupSetBinding,
    private val adapter: GroupsAdapter,
    private val snapshotViewModel: SnapshotViewModel,
    private val fragmentManager: FragmentManager,
) {
    private var boundKey: String? = null

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            update()
        }
    }

    private val dataObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = update()
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = update()
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = update()
        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = update()
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = update()
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) = update()
    }

    fun attach() {
        val root = overlay.root
        root.visibility = View.GONE
        root.elevation = root.resources.getDimension(R.dimen.group_set_sticky_elevation)
        root.outlineProvider = ViewOutlineProvider.BACKGROUND
        root.clipToOutline = true
        recyclerView.addOnScrollListener(scrollListener)
        adapter.registerAdapterDataObserver(dataObserver)
        update()
    }

    fun detach() {
        recyclerView.removeOnScrollListener(scrollListener)
        runCatching { adapter.unregisterAdapterDataObserver(dataObserver) }
    }

    fun update() {
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return hide()
        val first = lm.findFirstVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION) return hide()

        val headerPos = findOwningHeaderPosition(first) ?: return hide()
        val item = adapter.currentList.getOrNull(headerPos) as? ArchiveListItem.SetHeader
            ?: return hide()

        val headerChild = recyclerView.findViewHolderForAdapterPosition(headerPos)?.itemView
        if (headerChild != null && headerChild.top >= recyclerView.paddingTop) {
            return hide()
        }

        show(item)
        overlay.root.translationY = pushOffTranslation(headerPos, item.set.id)
    }

    private fun show(item: ArchiveListItem.SetHeader) {
        val key = "${item.set.id}|${item.name}|${item.groupCount}|${item.expanded}|${item.accentColor}"
        if (boundKey != key) {
            val surface = ContextCompat.getColor(overlay.root.context, R.color.surface)
            GroupSetHeaderBinder.bind(
                overlay, item, snapshotViewModel, fragmentManager, opaqueBackdrop = surface
            )
            boundKey = key
        }
        if (overlay.root.visibility != View.VISIBLE) {
            overlay.root.visibility = View.VISIBLE
        }
    }

    private fun hide() {
        if (overlay.root.visibility != View.GONE) {
            overlay.root.visibility = View.GONE
            overlay.root.translationY = 0f
        }
        boundKey = null
    }

    private fun pushOffTranslation(headerPos: Int, setId: String): Float {
        val boundary = findNextBoundaryPosition(headerPos, setId) ?: return 0f
        val nextView = recyclerView.findViewHolderForAdapterPosition(boundary)?.itemView ?: return 0f
        val stickyHeight = overlayHeight()
        val overlap = nextView.top - recyclerView.paddingTop - stickyHeight
        return min(0, overlap).toFloat()
    }

    private fun overlayHeight(): Int {
        val view = overlay.root
        if (view.height > 0) return view.height
        val width = recyclerView.width - recyclerView.paddingLeft - recyclerView.paddingRight
        if (width <= 0) return 0
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        return view.measuredHeight
    }

    private fun findOwningHeaderPosition(firstVisible: Int): Int? {
        val items = adapter.currentList
        return when (val item = items.getOrNull(firstVisible)) {
            is ArchiveListItem.SetHeader -> firstVisible
            is ArchiveListItem.EmptySetHint -> findHeaderIndex(item.set.id, firstVisible)
            is ArchiveListItem.GroupCard -> {
                val setId = item.setId ?: return null
                findHeaderIndex(setId, firstVisible)
            }
            null -> null
        }
    }

    private fun findHeaderIndex(setId: String, from: Int): Int? {
        val items = adapter.currentList
        for (i in from downTo 0) {
            val item = items[i]
            if (item is ArchiveListItem.SetHeader && item.set.id == setId) return i
        }
        return null
    }

    private fun findNextBoundaryPosition(headerPos: Int, setId: String): Int? {
        val items = adapter.currentList
        for (i in headerPos + 1 until items.size) {
            when (val item = items[i]) {
                is ArchiveListItem.SetHeader -> return i
                is ArchiveListItem.GroupCard -> if (item.setId != setId) return i
                is ArchiveListItem.EmptySetHint -> if (item.set.id != setId) return i
            }
        }
        return null
    }
}

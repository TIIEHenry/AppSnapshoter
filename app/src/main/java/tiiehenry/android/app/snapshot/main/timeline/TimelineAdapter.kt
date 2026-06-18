package tiiehenry.android.app.snapshot.main.timeline

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.databinding.ItemTimelineDateHeaderBinding
import tiiehenry.android.app.snapshot.databinding.ItemTimelineEntryBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimelineAdapter(
    private val onItemClick: (TimelineEntry) -> Unit,
    private val onMultiSelectModeChanged: (Boolean) -> Unit,
    private val onSelectionChanged: (Set<String>) -> Unit
) : ListAdapter<TimelineListItem, RecyclerView.ViewHolder>(ListItemDiffCallback()) {

    private var isMultiSelectMode = false
    private var selectedIds = emptySet<String>()
    private var expandedIds = emptySet<String>()
    var searchQuery: String = ""

    fun updateSearchQuery(query: String) {
        if (searchQuery == query) return
        searchQuery = query
        currentList.forEachIndexed { index, item ->
            if (item is TimelineListItem.Entry) {
                notifyItemChanged(index, PAYLOAD_HIGHLIGHT)
            }
        }
    }

    fun setMultiSelectMode(enabled: Boolean) {
        if (isMultiSelectMode != enabled) {
            isMultiSelectMode = enabled
            if (!enabled) {
                selectedIds = emptySet()
                expandedIds = emptySet()
            }
            if (itemCount > 0) {
                notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
            }
        }
    }

    fun setSelectedIds(ids: Set<String>) {
        val oldIds = selectedIds
        selectedIds = ids
        currentList.forEachIndexed { index, item ->
            if (item is TimelineListItem.Entry) {
                val id = item.entry.key.id
                val wasSelected = id in oldIds
                val isSelected = id in ids
                if (wasSelected != isSelected) {
                    notifyItemChanged(index, PAYLOAD_SELECTION)
                }
            }
        }
    }

    fun isDateHeader(position: Int): Boolean {
        return currentList.getOrNull(position) is TimelineListItem.DateHeader
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is TimelineListItem.DateHeader -> VIEW_TYPE_DATE_HEADER
            is TimelineListItem.Entry -> VIEW_TYPE_ENTRY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_DATE_HEADER -> {
                val binding = ItemTimelineDateHeaderBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                DateHeaderViewHolder(binding)
            }
            else -> {
                val binding = ItemTimelineEntryBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                EntryViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is DateHeaderViewHolder -> holder.bind(getItem(position) as TimelineListItem.DateHeader)
            is EntryViewHolder -> holder.bind(getItem(position) as TimelineListItem.Entry)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }
        when (holder) {
            is EntryViewHolder -> {
                val item = getItem(position) as TimelineListItem.Entry
                when (payloads[0]) {
                    PAYLOAD_SELECTION -> holder.bindSelection(item.entry)
                    PAYLOAD_EXPAND -> holder.bindExpand(item.entry)
                    PAYLOAD_HIGHLIGHT -> holder.bindHighlight(item.entry)
                }
            }
        }
    }

    inner class DateHeaderViewHolder(
        private val binding: ItemTimelineDateHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TimelineListItem.DateHeader) {
            binding.dateHeaderLabel.text = item.label
        }
    }

    inner class EntryViewHolder(
        private val binding: ItemTimelineEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.itemContent.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val item = getItem(pos) as? TimelineListItem.Entry ?: return@setOnClickListener
                if (isMultiSelectMode) {
                    toggleSelection(item.entry, pos)
                } else {
                    onItemClick(item.entry)
                }
            }

            binding.expandButton.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val item = getItem(pos) as? TimelineListItem.Entry ?: return@setOnClickListener
                if (isMultiSelectMode) return@setOnClickListener
                toggleExpand(item.entry, pos)
            }

            val enterMultiSelectOnLongPress = View.OnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@OnLongClickListener true
                val item = getItem(pos) as? TimelineListItem.Entry ?: return@OnLongClickListener true
                enterMultiSelectWithItem(item.entry)
                true
            }
            binding.itemContent.setOnLongClickListener(enterMultiSelectOnLongPress)
            binding.expandButton.setOnLongClickListener(enterMultiSelectOnLongPress)
        }

        private fun enterMultiSelectWithItem(entry: TimelineEntry) {
            if (isMultiSelectMode) return
            isMultiSelectMode = true
            onMultiSelectModeChanged(true)
            selectedIds = setOf(entry.key.id)
            onSelectionChanged(selectedIds)
            if (itemCount > 0) {
                notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
            }
        }

        fun bindHighlight(entry: TimelineEntry) {
            val context = binding.root.context
            val query = searchQuery.trim()
            binding.appName.text = TimelineTextHighlight.highlight(context, entry.appLabel, query)
            binding.groupName.text = TimelineTextHighlight.highlight(context, entry.groupName, query)
        }

        @SuppressLint("SetTextI18n")
        fun bind(item: TimelineListItem.Entry) {
            val entry = item.entry
            bindSelection(entry)
            bindExpand(entry)
            bindHighlight(entry)

            val context = binding.root.context
            val count = entry.matchingArchiveNames.size
            val summary = if (count == 1) {
                val time = DATE_FORMAT_SHORT.format(Date(entry.matchingArchiveTimes[0]))
                context.getString(R.string.timeline_snapshot_count_single, time)
            } else {
                val latest = DATE_FORMAT_SHORT.format(Date(entry.matchingArchiveTimes.first()))
                val oldest = DATE_FORMAT_SHORT.format(Date(entry.matchingArchiveTimes.last()))
                context.getString(R.string.timeline_snapshot_count_multi, count, oldest, latest)
            }
            binding.archiveSummary.text = summary

            val iconFile = entry.iconFile
            if (File(iconFile).exists()) {
                Glide.with(binding.appIcon)
                    .load(iconFile)
                    .into(binding.appIcon)
            } else {
                Glide.with(binding.appIcon).clear(binding.appIcon)
                binding.appIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }
        }

        fun bindSelection(entry: TimelineEntry) {
            if (isMultiSelectMode) {
                binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = entry.key.id in selectedIds
                binding.itemContent.isActivated = entry.key.id in selectedIds
                binding.expandButton.visibility = View.GONE
            } else {
                binding.checkbox.visibility = View.GONE
                binding.itemContent.isActivated = false
                binding.expandButton.visibility = View.VISIBLE
            }
        }

        fun bindExpand(entry: TimelineEntry) {
            val isExpanded = entry.key.id in expandedIds
            binding.snapshotDetails.visibility = if (isExpanded) View.VISIBLE else View.GONE
            binding.expandButton.contentDescription = if (isExpanded) {
                binding.root.context.getString(R.string.timeline_collapse_desc)
            } else {
                binding.root.context.getString(R.string.timeline_expand_desc)
            }
            binding.expandButton.rotation = if (isExpanded) 180f else 0f
            if (isExpanded) {
                populateSnapshotDetails(entry)
            }
        }

        private fun populateSnapshotDetails(entry: TimelineEntry) {
            val container = binding.snapshotDetails
            val names = entry.matchingArchiveNames
            val times = entry.matchingArchiveTimes
            val childCount = container.childCount
            val needCount = names.size
            val context = binding.root.context
            val detailColor = MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                0
            )
            for (i in 0 until needCount) {
                val textView = if (i < childCount) {
                    container.getChildAt(i) as android.widget.TextView
                } else {
                    android.widget.TextView(context).also {
                        it.textSize = 12f
                        it.setTextColor(detailColor)
                        container.addView(it)
                    }
                }
                textView.text = "${names[i]}  ${DATE_FORMAT_FULL.format(Date(times[i]))}"
                textView.setPadding(0, if (i == 0) 0 else 2, 0, 2)
            }
            if (childCount > needCount) {
                container.removeViews(needCount, childCount - needCount)
            }
        }

        private fun toggleSelection(entry: TimelineEntry, pos: Int) {
            val newIds = if (entry.key.id in selectedIds) {
                selectedIds - entry.key.id
            } else {
                selectedIds + entry.key.id
            }
            selectedIds = newIds
            onSelectionChanged(newIds)
            notifyItemChanged(pos, PAYLOAD_SELECTION)
        }

        private fun toggleExpand(entry: TimelineEntry, pos: Int) {
            expandedIds = if (entry.key.id in expandedIds) {
                expandedIds - entry.key.id
            } else {
                expandedIds + entry.key.id
            }
            notifyItemChanged(pos, PAYLOAD_EXPAND)
        }
    }

    private class ListItemDiffCallback : DiffUtil.ItemCallback<TimelineListItem>() {
        override fun areItemsTheSame(oldItem: TimelineListItem, newItem: TimelineListItem): Boolean {
            return when {
                oldItem is TimelineListItem.DateHeader && newItem is TimelineListItem.DateHeader ->
                    oldItem.epochDay == newItem.epochDay
                oldItem is TimelineListItem.Entry && newItem is TimelineListItem.Entry ->
                    oldItem.entry.key.id == newItem.entry.key.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: TimelineListItem, newItem: TimelineListItem): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private const val VIEW_TYPE_DATE_HEADER = 0
        private const val VIEW_TYPE_ENTRY = 1
        private const val PAYLOAD_SELECTION = "selection"
        private const val PAYLOAD_EXPAND = "expand"
        private const val PAYLOAD_HIGHLIGHT = "highlight"
        private val DATE_FORMAT_SHORT = SimpleDateFormat("MM/dd", Locale.getDefault())
        private val DATE_FORMAT_FULL = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }
}

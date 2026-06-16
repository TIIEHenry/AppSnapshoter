package tiiehenry.android.app.snapshot.main.timeline

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.databinding.ItemTimelineEntryBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimelineAdapter(
    private val onItemClick: (TimelineEntry) -> Unit,
    private val onMultiSelectModeChanged: (Boolean) -> Unit,
    private val onSelectionChanged: (Set<String>) -> Unit
) : ListAdapter<TimelineEntry, TimelineAdapter.ViewHolder>(EntryDiffCallback()) {

    private var isMultiSelectMode = false
    private var selectedIds = emptySet<String>()
    private var expandedIds = emptySet<String>()

    fun setMultiSelectMode(enabled: Boolean) {
        if (isMultiSelectMode != enabled) {
            isMultiSelectMode = enabled
            if (!enabled) selectedIds = emptySet()
            if (itemCount > 0) {
                notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
            }
        }
    }

    fun setSelectedIds(ids: Set<String>) {
        val oldIds = selectedIds
        selectedIds = ids
        currentList.forEachIndexed { index, entry ->
            val wasSelected = entry.key.id in oldIds
            val isSelected = entry.key.id in ids
            if (wasSelected != isSelected) {
                notifyItemChanged(index, PAYLOAD_SELECTION)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTimelineEntryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            when (payloads[0]) {
                PAYLOAD_SELECTION -> holder.bindSelection(getItem(position))
                PAYLOAD_EXPAND -> holder.bindExpand(getItem(position))
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class ViewHolder(
        private val binding: ItemTimelineEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val entry = getItem(pos)
                if (isMultiSelectMode) {
                    val newIds = if (entry.key.id in selectedIds) {
                        selectedIds - entry.key.id
                    } else {
                        selectedIds + entry.key.id
                    }
                    selectedIds = newIds
                    onSelectionChanged(newIds)
                    notifyItemChanged(pos, PAYLOAD_SELECTION)
                } else {
                    expandedIds = if (entry.key.id in expandedIds) {
                        expandedIds - entry.key.id
                    } else {
                        expandedIds + entry.key.id
                    }
                    notifyItemChanged(pos, PAYLOAD_EXPAND)
                }
            }
            binding.root.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnLongClickListener true
                if (!isMultiSelectMode) {
                    isMultiSelectMode = true
                    onMultiSelectModeChanged(true)
                    val entry = getItem(pos)
                    selectedIds = setOf(entry.key.id)
                    onSelectionChanged(selectedIds)
                    if (itemCount > 0) {
                        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
                    }
                }
                true
            }
        }

        fun bind(entry: TimelineEntry) {
            bindingSelection(entry)
            bindExpand(entry)
            binding.appName.text = entry.appLabel
            binding.groupName.text = entry.groupName

            val count = entry.matchingArchiveNames.size
            val context = binding.root.context
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
            bindingSelection(entry)
        }

        fun bindExpand(entry: TimelineEntry) {
            val isExpanded = entry.key.id in expandedIds
            binding.snapshotDetails.visibility = if (isExpanded) View.VISIBLE else View.GONE
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
            // Reuse existing TextViews, add or remove as needed
            for (i in 0 until needCount) {
                val textView = if (i < childCount) {
                    container.getChildAt(i) as android.widget.TextView
                } else {
                    android.widget.TextView(context).also {
                        it.textSize = 12f
                        it.setTextColor(context.getColor(android.R.color.darker_gray))
                        container.addView(it)
                    }
                }
                textView.text = "${names[i]}  ${DATE_FORMAT_FULL.format(Date(times[i]))}"
                textView.setPadding(0, if (i == 0) 0 else 2, 0, 2)
            }
            // Remove excess views
            if (childCount > needCount) {
                container.removeViews(needCount, childCount - needCount)
            }
        }

        private fun bindingSelection(entry: TimelineEntry) {
            if (isMultiSelectMode) {
                binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = entry.key.id in selectedIds
                binding.root.isActivated = entry.key.id in selectedIds
            } else {
                binding.checkbox.visibility = View.GONE
                binding.root.isActivated = false
            }
        }
    }

    private class EntryDiffCallback : DiffUtil.ItemCallback<TimelineEntry>() {
        override fun areItemsTheSame(oldItem: TimelineEntry, newItem: TimelineEntry): Boolean {
            return oldItem.key.id == newItem.key.id
        }

        override fun areContentsTheSame(oldItem: TimelineEntry, newItem: TimelineEntry): Boolean {
            if (oldItem.appLabel != newItem.appLabel) return false
            if (oldItem.groupName != newItem.groupName) return false
            if (oldItem.iconFile != newItem.iconFile) return false
            if (oldItem.matchingArchiveNames != newItem.matchingArchiveNames) return false
            if (oldItem.matchingArchiveTimes != newItem.matchingArchiveTimes) return false
            return true
        }
    }

    companion object {
        private const val PAYLOAD_SELECTION = "selection"
        private const val PAYLOAD_EXPAND = "expand"
        private val DATE_FORMAT_SHORT = SimpleDateFormat("MM/dd", Locale.getDefault())
        private val DATE_FORMAT_FULL = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }
}

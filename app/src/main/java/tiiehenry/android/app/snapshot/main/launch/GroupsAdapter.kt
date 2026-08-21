package tiiehenry.android.app.snapshot.main.launch

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SnapshotViewModel
import tiiehenry.android.app.snapshot.config.SortConfig
import tiiehenry.android.app.snapshot.databinding.ItemEmptySetHintBinding
import tiiehenry.android.app.snapshot.databinding.ItemGroupBinding
import tiiehenry.android.app.snapshot.databinding.ItemGroupSetBinding
import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.app.snapshot.group.GroupSetColors
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.main.launch.addgroup.AddGroupBottomSheet
import tiiehenry.android.app.snapshot.main.launch.groupset.GroupSetSettingFragment
import java.nio.file.Paths

class GroupsAdapter(
    private val viewModel: LauncherViewModel,
    private val snapshotViewModel: SnapshotViewModel,
    private val fragmentManager: FragmentManager
) : ListAdapter<ArchiveListItem, RecyclerView.ViewHolder>(ArchiveDiffCallback()) {

    var isBatchRunning: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount, BATCH_RUNNING_PAYLOAD)
        }

    companion object {
        private const val BATCH_RUNNING_PAYLOAD = "batch_running"
        private const val VIEW_TYPE_SET = 1
        private const val VIEW_TYPE_GROUP = 2
        private const val VIEW_TYPE_EMPTY_HINT = 3
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ArchiveListItem.SetHeader -> VIEW_TYPE_SET
        is ArchiveListItem.GroupCard -> VIEW_TYPE_GROUP
        is ArchiveListItem.EmptySetHint -> VIEW_TYPE_EMPTY_HINT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SET -> {
                val binding = ItemGroupSetBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                SetHeaderViewHolder(binding, snapshotViewModel, fragmentManager)
            }
            VIEW_TYPE_EMPTY_HINT -> {
                val binding = ItemEmptySetHintBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                EmptySetHintViewHolder(binding, fragmentManager)
            }
            else -> {
                val binding = ItemGroupBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                GroupViewHolder(binding, viewModel, snapshotViewModel, fragmentManager)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(BATCH_RUNNING_PAYLOAD) && holder is GroupViewHolder) {
            holder.applyBatchRunningState(isBatchRunning)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ArchiveListItem.SetHeader -> (holder as SetHeaderViewHolder).bind(item)
            is ArchiveListItem.EmptySetHint -> (holder as EmptySetHintViewHolder).bind(item)
            is ArchiveListItem.GroupCard ->
                (holder as GroupViewHolder).bind(
                    this,
                    item.group,
                    inSet = item.setId != null,
                    accentColor = item.accentColor,
                )
        }
    }

    class EmptySetHintViewHolder(
        private val binding: ItemEmptySetHintBinding,
        private val fragmentManager: FragmentManager,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ArchiveListItem.EmptySetHint) {
            binding.hintMembershipRail.setBackgroundColor(item.accentColor)
            binding.root.setOnClickListener {
                val suggested = Paths.get(item.set.path, "group").toString()
                AddGroupBottomSheet.newInstance(
                    suggestedPath = suggested,
                    suggestedName = "group",
                ).show(fragmentManager, AddGroupBottomSheet.TAG)
            }
        }
    }

    class SetHeaderViewHolder(
        private val binding: ItemGroupSetBinding,
        private val snapshotViewModel: SnapshotViewModel,
        private val fragmentManager: FragmentManager,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ArchiveListItem.SetHeader) {
            val set = item.set
            val accent = item.accentColor
            binding.setTitle.text = item.name
            binding.setCount.text = binding.root.context.getString(
                R.string.group_set_count_format,
                item.groupCount,
            )
            binding.setExpandIcon.rotation = if (item.expanded) 0f else -90f
            ImageViewCompat.setImageTintList(
                binding.setExpandIcon,
                ColorStateList.valueOf(accent),
            )
            val headerBg = (ContextCompat.getDrawable(
                binding.root.context,
                R.drawable.bg_group_set_header,
            )?.mutate() as? GradientDrawable)
            headerBg?.setColor(GroupSetColors.headerBackground(accent))
            binding.root.background = headerBg

            binding.setTitle.setOnClickListener {
                snapshotViewModel.setGroupSetCollapsed(set.id, collapsed = item.expanded)
            }
            binding.setExpandIcon.setOnClickListener {
                binding.setTitle.performClick()
            }
            binding.setTitle.setOnLongClickListener {
                GroupSetSettingFragment.newInstance(set.id).show(fragmentManager, GroupSetSettingFragment.TAG)
                true
            }
            binding.btnTune.setOnClickListener {
                GroupSetSettingFragment.newInstance(set.id).show(fragmentManager, GroupSetSettingFragment.TAG)
            }
            binding.btnRefresh.setOnClickListener {
                snapshotViewModel.refreshGroupSet(set.id) { count ->
                    Toast.makeText(
                        binding.root.context,
                        binding.root.context.getString(R.string.group_set_refresh_result, count),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    class GroupViewHolder(
        val binding: ItemGroupBinding,
        private val viewModel: LauncherViewModel,
        private val snapshotViewModel: SnapshotViewModel,
        val fragmentManager: FragmentManager
    ) : RecyclerView.ViewHolder(binding.root) {

        var isSortMode = false
        private var itemTouchHelper: ItemTouchHelper? = null
        private lateinit var actionsController: GroupActionsController
        private var boundGroup: SnapGroup? = null

        fun bind(
            groupsAdapter: GroupsAdapter,
            group: SnapGroup,
            inSet: Boolean,
            accentColor: Int?,
        ) {
            boundGroup = group
            binding.groupTitle.text = group.name
            if (inSet && accentColor != null) {
                binding.setMembershipRail.visibility = View.VISIBLE
                binding.setMembershipRail.setBackgroundColor(accentColor)
            } else {
                binding.setMembershipRail.visibility = View.GONE
            }

            actionsController = GroupActionsController(
                binding, viewModel, snapshotViewModel, fragmentManager
            ) { g -> refresh(g, binding.groupRecyclerView) }
            actionsController.setupActions(group, groupsAdapter, this)
            actionsController.setBatchRunning(groupsAdapter.isBatchRunning)

            binding.groupRecyclerView.layoutManager = GridLayoutManager(binding.root.context, 4)

            val adapter = GroupItemAdapter(
                this, groupsAdapter, viewModel, snapshotViewModel, group,
                groupsAdapter.isBatchRunning,
            ) { adapter, item ->
                val currentList = ArrayList(group.apps)
                val index = currentList.indexOfFirst { it.appInfo.packageName == item.appInfo.packageName }
                if (index != -1) {
                    currentList[index] = item
                    adapter.submitList(currentList)
                }
            }
            binding.groupRecyclerView.adapter = adapter

            refresh(group, binding.groupRecyclerView)

            binding.emptyLayout.setOnClickListener {
                binding.btnAdd.performClick()
            }

            syncChromeVisibility()
        }

        fun applyBatchRunningState(running: Boolean) {
            if (!::actionsController.isInitialized) return
            actionsController.setBatchRunning(running)
            (binding.groupRecyclerView.adapter as? GroupItemAdapter)?.setBatchRunning(running)
        }

        fun toggleSortMode(group: SnapGroup, adapter: GroupItemAdapter) {
            isSortMode = !isSortMode
            boundGroup = group
            if (isSortMode) {
                startDragSortMode(adapter, group)
                binding.groupTitle.text = binding.root.context.getString(R.string.group_sort_mode_title, group.name)
            } else {
                binding.groupTitle.text = group.name
                stopDragSortMode(adapter)
            }
            syncChromeVisibility()
        }

        private fun startDragSortMode(adapter: GroupItemAdapter, group: SnapGroup) {
            val callback = object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
            ) {
                override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                    val fromPosition = vh.adapterPosition
                    val toPosition = target.adapterPosition
                    val currentList = adapter.currentList.toMutableList()
                    val item = currentList[fromPosition]
                    currentList.removeAt(fromPosition)
                    currentList.add(toPosition, item)
                    adapter.submitList(currentList)
                    saveSortOrderToConfig(currentList, group)
                    return true
                }

                override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
                override fun isLongPressDragEnabled() = false
                override fun isItemViewSwipeEnabled() = false
            }
            itemTouchHelper = ItemTouchHelper(callback)
            itemTouchHelper?.attachToRecyclerView(binding.groupRecyclerView)
            adapter.itemTouchHelper = itemTouchHelper
            adapter.notifyDataSetChanged()
        }

        private fun stopDragSortMode(adapter: GroupItemAdapter) {
            itemTouchHelper?.attachToRecyclerView(null)
            itemTouchHelper = null
            adapter.itemTouchHelper = null
            adapter.notifyDataSetChanged()
        }

        private fun saveSortOrderToConfig(sortedList: List<ArchivedApp>, group: SnapGroup) {
            val sortConfig = group.config.sortConfig
            sortConfig.sortOrder = sortedList.map { it.appInfo.packageName }.toMutableList()
            sortConfig.sortType = SortConfig.SORT_TYPE_CUSTOM
            group.config.save()
        }

        fun refresh(group: SnapGroup, recyclerView: RecyclerView) {
            boundGroup = group
            binding.groupTitle.text = group.name
            val sortedApps = synchronized(group.apps) {
                applySorting(group.apps, group.config.sortConfig, group)
            }
            Log.i("GroupsAdapter", "refresh $sortedApps")
            val adapter = recyclerView.adapter as GroupItemAdapter
            adapter.submitList(sortedApps)
            adapter.notifyDataSetChanged()
            recyclerView.invalidate()
            recyclerView.requestLayout()
            renderBody(group)
            syncChromeVisibility()
        }

        fun updateCollapseState(@Suppress("UNUSED_PARAMETER") isCollapsed: Boolean) {
            val group = boundGroup ?: return
            renderBody(group)
            syncChromeVisibility()
        }

        private fun renderBody(group: SnapGroup) {
            binding.progressBar.visibility = View.GONE
            when {
                group.isCollapsed -> {
                    binding.expandGroup.visibility = View.VISIBLE
                    binding.emptyLayout.visibility = View.GONE
                    binding.appLayout.visibility = View.GONE
                }
                group.apps.isEmpty() -> {
                    binding.expandGroup.visibility = View.GONE
                    binding.emptyLayout.visibility = View.VISIBLE
                    binding.appLayout.visibility = View.GONE
                    binding.groupRecyclerView.visibility = View.GONE
                }
                else -> {
                    binding.expandGroup.visibility = View.GONE
                    binding.emptyLayout.visibility = View.GONE
                    binding.appLayout.visibility = View.VISIBLE
                    binding.groupRecyclerView.visibility = View.VISIBLE
                }
            }
        }

        private fun syncChromeVisibility() {
            if (!::actionsController.isInitialized) return
            val showActions = !isSortMode
            val isEmpty = boundGroup?.apps?.isEmpty() == true
            actionsController.updateButtonVisibility(showActions, isEmpty = isEmpty)
        }

        private fun applySorting(apps: List<ArchivedApp>, sortConfig: SortConfig, group: SnapGroup): List<ArchivedApp> {
            return when (sortConfig.sortType) {
                SortConfig.SORT_TYPE_CUSTOM -> {
                    val sortOrder = sortConfig.sortOrder.toMutableList()
                    val appPackageNames = apps.map { it.appInfo.packageName }
                    sortOrder.removeAll { it !in appPackageNames }
                    val newApps = appPackageNames.filter { it !in sortOrder }
                    sortOrder.addAll(newApps)
                    if (sortOrder != sortConfig.sortOrder) {
                        sortConfig.sortOrder = sortOrder
                        group.config.save()
                    }
                    apps.sortedBy { sortOrder.indexOf(it.appInfo.packageName) }
                }
                SortConfig.SORT_TYPE_NAME_DESC -> apps.sortedByDescending { it.appInfo.label }
                SortConfig.SORT_TYPE_NAME_ASC -> apps.sortedBy { it.appInfo.label }
                SortConfig.SORT_TYPE_INSTALL_TIME_ASC -> apps.sortedBy { it.appInfo.packageInfo?.firstInstallTime ?: 0L }
                SortConfig.SORT_TYPE_INSTALL_TIME_DESC -> apps.sortedByDescending { it.appInfo.packageInfo?.firstInstallTime ?: 0L }
                else -> apps.toList()
            }
        }
    }

    private class ArchiveDiffCallback : DiffUtil.ItemCallback<ArchiveListItem>() {
        override fun areItemsTheSame(oldItem: ArchiveListItem, newItem: ArchiveListItem): Boolean {
            return when {
                oldItem is ArchiveListItem.SetHeader && newItem is ArchiveListItem.SetHeader ->
                    oldItem.set.id == newItem.set.id
                oldItem is ArchiveListItem.GroupCard && newItem is ArchiveListItem.GroupCard ->
                    oldItem.group.id == newItem.group.id
                oldItem is ArchiveListItem.EmptySetHint && newItem is ArchiveListItem.EmptySetHint ->
                    oldItem.set.id == newItem.set.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: ArchiveListItem, newItem: ArchiveListItem): Boolean {
            return when {
                oldItem is ArchiveListItem.SetHeader && newItem is ArchiveListItem.SetHeader ->
                    oldItem.name == newItem.name &&
                        oldItem.groupCount == newItem.groupCount &&
                        oldItem.expanded == newItem.expanded &&
                        oldItem.accentColor == newItem.accentColor
                oldItem is ArchiveListItem.EmptySetHint && newItem is ArchiveListItem.EmptySetHint ->
                    oldItem.set.path == newItem.set.path &&
                        oldItem.accentColor == newItem.accentColor
                oldItem is ArchiveListItem.GroupCard && newItem is ArchiveListItem.GroupCard -> {
                    val o = oldItem.group
                    val n = newItem.group
                    if (o.name != n.name) return false
                    if (o.isCollapsed != n.isCollapsed) return false
                    if (oldItem.setId != newItem.setId) return false
                    if (oldItem.accentColor != newItem.accentColor) return false
                    val oldPkgs = o.apps.map { it.appInfo.packageName }
                    val newPkgs = n.apps.map { it.appInfo.packageName }
                    oldPkgs == newPkgs
                }
                else -> false
            }
        }
    }
}

package tiiehenry.android.app.snapshot.main.launch

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import tiiehenry.android.app.snapshot.SnapshotViewModel
import tiiehenry.android.app.snapshot.config.SortConfig
import tiiehenry.android.app.snapshot.databinding.ItemGroupBinding
import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.app.snapshot.group.SnapGroup

class GroupsAdapter(
    private val viewModel: LauncherViewModel,
    private val snapshotViewModel: SnapshotViewModel,
    private val fragmentManager: FragmentManager
) : ListAdapter<SnapGroup, GroupsAdapter.GroupViewHolder>(GroupDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GroupViewHolder(binding, viewModel, snapshotViewModel, fragmentManager)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(this, getItem(position))
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

        fun bind(groupsAdapter: GroupsAdapter, group: SnapGroup) {
            binding.groupTitle.text = group.name
            updateCollapseState(group.isCollapsed)

            actionsController = GroupActionsController(
                binding, viewModel, snapshotViewModel, fragmentManager
            ) { g -> refresh(g, binding.groupRecyclerView) }
            actionsController.setupActions(group, groupsAdapter, this)

            binding.groupRecyclerView.layoutManager = GridLayoutManager(binding.root.context, 4)

            val adapter = GroupItemAdapter(this, groupsAdapter, viewModel, snapshotViewModel, group) { adapter, item ->
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
                actionsController.setupActions(group, groupsAdapter, this)
            }

            actionsController.updateButtonVisibility(!isSortMode)
        }

        fun toggleSortMode(group: SnapGroup, adapter: GroupItemAdapter) {
            isSortMode = !isSortMode
            if (isSortMode) {
                startDragSortMode(adapter, group)
                binding.groupTitle.text = "${group.name} (排序模式)"
                actionsController.updateButtonVisibility(false)
            } else {
                binding.groupTitle.text = group.name
                stopDragSortMode(adapter)
                actionsController.updateButtonVisibility(true)
            }
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
            binding.groupTitle.text = group.name
            if (group.apps.isEmpty()) {
                binding.progressBar.visibility = View.GONE
                binding.groupRecyclerView.visibility = View.GONE
                binding.emptyLayout.visibility = View.VISIBLE
                binding.btnAdd.visibility = View.GONE
            } else {
                binding.progressBar.visibility = View.GONE
                binding.groupRecyclerView.visibility = View.VISIBLE
                binding.emptyLayout.visibility = View.GONE
                binding.btnAdd.visibility = View.VISIBLE
            }
            val sortedApps = synchronized(group.apps) {
                applySorting(group.apps, group.config.sortConfig, group)
            }
            Log.i("GroupsAdapter", "refresh $sortedApps")
            val adapter = recyclerView.adapter as GroupItemAdapter
            adapter.submitList(sortedApps)
            adapter.notifyDataSetChanged()
            recyclerView.invalidate()
            recyclerView.requestLayout()
            updateCollapseState(group.isCollapsed)
        }

        fun updateCollapseState(isCollapsed: Boolean) {
            binding.appLayout.visibility = if (isCollapsed) View.GONE else View.VISIBLE
            binding.expandGroup.visibility = if (isCollapsed) View.VISIBLE else View.GONE
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

    private class GroupDiffCallback : DiffUtil.ItemCallback<SnapGroup>() {
        override fun areItemsTheSame(oldItem: SnapGroup, newItem: SnapGroup) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: SnapGroup, newItem: SnapGroup) = oldItem == newItem
    }
}

package tiiehenry.android.app.snapshot.main.launch

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.config.SortConfig
import tiiehenry.android.app.snapshot.databinding.ItemGroupBinding
import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.main.launch.group.GroupConfigFragment
import tiiehenry.android.app.snapshot.main.launch.group.GroupSettingFragment
import tiiehenry.android.app.snapshot.main.selectapp.SelectAppFragment

class GroupsAdapter(
    private val viewModel: LauncherViewModel,
    private val fragmentManager: FragmentManager
) : ListAdapter<SnapGroup, GroupsAdapter.GroupViewHolder>(GroupDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GroupViewHolder(binding, viewModel, fragmentManager)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(this, getItem(position))
    }

    class GroupViewHolder(
        val binding: ItemGroupBinding,
        private val viewModel: LauncherViewModel,
        val fragmentManager: FragmentManager
    ) : RecyclerView.ViewHolder(binding.root) {

        var isSortMode = false
        private var itemTouchHelper: ItemTouchHelper? = null
        private var archiver: GroupBatchArchiver? = null

        fun addNewApp(group: SnapGroup) {
            SelectAppFragment.newInstance(group.id) { appInfos ->
                SnapshotApp.getViewModel().addAppsToGroup(group.id, appInfos) {
                    val updatedGroup =
                        SnapshotApp.getViewModel().groupList.value?.find { it.id == group.id }
                    refresh(updatedGroup ?: group, binding.groupRecyclerView)
                }
            }.show(fragmentManager, "SelectAppFragment")
        }

        fun bind(groupsAdapter: GroupsAdapter, group: SnapGroup) {
            binding.groupTitle.text = group.name
            updateCollapseState(group.isCollapsed)

            archiver = GroupBatchArchiver(binding.root.context, viewModel.viewModelScope) { g ->
                refresh(g, binding.groupRecyclerView)
            }

            binding.groupTitle.setOnClickListener {
                group.isCollapsed = !group.isCollapsed
                updateCollapseState(group.isCollapsed)
            }
            binding.groupTitle.setOnLongClickListener {
                GroupSettingFragment.newInstance(group) {
                    refresh(group, binding.groupRecyclerView)
                }.show(fragmentManager, "GroupConfigFragment")
                true
            }

            binding.expandGroup.setOnClickListener {
                group.isCollapsed = false
                updateCollapseState(group.isCollapsed)
            }

            binding.groupRecyclerView.layoutManager = GridLayoutManager(binding.root.context, 4)

            val adapter = GroupItemAdapter(this, groupsAdapter, viewModel, group) { adapter, item ->
                val currentList = ArrayList(group.apps)
                val index = currentList.indexOfFirst { it.appInfo.packageName == item.appInfo.packageName }
                if (index != -1) {
                    currentList[index] = item
                    adapter.submitList(currentList)
                }
            }
            binding.groupRecyclerView.adapter = adapter

            binding.btnRefresh.setOnClickListener {
                viewModel.viewModelScope.launch {
                    group.loadApps(
                        SnapshotApp.getContext(),
                        SnapshotApp.getInstance().fileSystem,
                        SnapshotApp.getInstance().appManager,
                        reload = true
                    )
                    withContext(Dispatchers.Main) { refresh(group, binding.groupRecyclerView) }
                }
            }
            binding.btnRefresh.setOnLongClickListener {
                archiver?.showGroupStatistics(group)
                true
            }
            refresh(group, binding.groupRecyclerView)

            binding.btnAdd.setOnClickListener { addNewApp(group) }
            binding.btnAdd.setOnLongClickListener {
                Toast.makeText(it.context, "添加新应用到分组", Toast.LENGTH_SHORT).show()
                true
            }

            binding.emptyLayout.setOnClickListener { addNewApp(group) }

            binding.btnMove.setOnClickListener { v -> showSortTypePopupMenu(v, group, adapter) }
            if (group.config.sortConfig.sortType == SortConfig.SORT_TYPE_CUSTOM) {
                binding.btnMove.setOnLongClickListener {
                    toggleSortMode(group, adapter)
                    true
                }
            }
            binding.btnConfirm.setOnClickListener { toggleSortMode(group, adapter) }

            binding.btnTune.setOnClickListener {
                GroupConfigFragment.newInstance(group) {
                    refresh(group, binding.groupRecyclerView)
                }.show(fragmentManager, "GroupShotConfigFragment")
            }
            binding.btnTune.setOnLongClickListener {
                Toast.makeText(it.context, "设置分组配置", Toast.LENGTH_SHORT).show()
                true
            }

            binding.btnArchiveAll.setOnClickListener {
                archiver?.archiveAllApps(group)
            }
            binding.btnArchiveAll.setOnLongClickListener {
                Toast.makeText(it.context, "一键存档", Toast.LENGTH_SHORT).show()
                true
            }

            updateButtonVisibility(!isSortMode)
        }

        private fun showSortTypePopupMenu(anchor: View, group: SnapGroup, adapter: GroupItemAdapter) {
            val popup = PopupMenu(anchor.context, anchor)
            val menu = popup.menu
            val sortTypes = listOf(
                SortConfig.SORT_TYPE_DEFAULT to "默认排序",
                SortConfig.SORT_TYPE_NAME_ASC to "按名称升序",
                SortConfig.SORT_TYPE_NAME_DESC to "按名称降序",
                SortConfig.SORT_TYPE_INSTALL_TIME_ASC to "按安装时间升序",
                SortConfig.SORT_TYPE_INSTALL_TIME_DESC to "按安装时间降序",
                SortConfig.SORT_TYPE_CUSTOM to "自定义排序"
            )
            val currentSortType = group.config.sortConfig.sortType
            sortTypes.forEachIndexed { index, (type, label) ->
                val item = menu.add(0, type, index, label)
                item.isCheckable = true
                item.isChecked = (type == currentSortType)
            }
            popup.setOnMenuItemClickListener { menuItem ->
                val newSortType = menuItem.itemId
                group.config.sortConfig.sortType = newSortType
                group.config.save()
                if (newSortType == SortConfig.SORT_TYPE_CUSTOM) {
                    binding.btnMove.setOnLongClickListener {
                        toggleSortMode(group, adapter)
                        true
                    }
                } else {
                    binding.btnMove.setOnLongClickListener(null)
                    if (isSortMode) toggleSortMode(group, adapter)
                }
                refresh(group, binding.groupRecyclerView)
                true
            }
            popup.show()
        }

        private fun toggleSortMode(group: SnapGroup, adapter: GroupItemAdapter) {
            isSortMode = !isSortMode
            if (isSortMode) {
                startDragSortMode(adapter, group)
                binding.groupTitle.text = "${group.name} (排序模式)"
                updateButtonVisibility(false)
            } else {
                binding.groupTitle.text = group.name
                stopDragSortMode(adapter)
                updateButtonVisibility(true)
            }
        }

        private fun updateButtonVisibility(show: Boolean) {
            binding.btnMove.visibility = if (show) View.VISIBLE else View.GONE
            binding.btnAdd.visibility = if (show) View.VISIBLE else View.GONE
            binding.btnTune.visibility = if (show) View.VISIBLE else View.GONE
            binding.btnRefresh.visibility = if (show) View.VISIBLE else View.GONE
            binding.btnArchiveAll.visibility = if (show) View.VISIBLE else View.GONE
            binding.btnConfirm.visibility = if (show) View.GONE else View.VISIBLE
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

        private fun updateCollapseState(isCollapsed: Boolean) {
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

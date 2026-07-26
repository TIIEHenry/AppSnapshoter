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
import tiiehenry.android.app.snapshot.R
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

    var isBatchRunning: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount, BATCH_RUNNING_PAYLOAD)
        }

    companion object {
        private const val BATCH_RUNNING_PAYLOAD = "batch_running"
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(BATCH_RUNNING_PAYLOAD)) {
            holder.applyBatchRunningState(isBatchRunning)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

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
        /** 当前绑定的分组；折叠点击等半更新路径经此取完整模型再投影。 */
        private var boundGroup: SnapGroup? = null

        fun bind(groupsAdapter: GroupsAdapter, group: SnapGroup) {
            boundGroup = group
            binding.groupTitle.text = group.name

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

        /**
         * 折叠点击后由 Controller 调用：已写入 [SnapGroup.isCollapsed]，再按完整模型投影 body。
         * 禁止在此只改 expand/app 而不管 empty。
         */
        fun updateCollapseState(@Suppress("UNUSED_PARAMETER") isCollapsed: Boolean) {
            val group = boundGroup ?: return
            renderBody(group)
            syncChromeVisibility()
        }

        /**
         * Body 三 sibling 不变量：`expand_group` / `empty_layout` / `app_layout` 至多一个 VISIBLE。
         * 折叠优先于空组。新触点禁止直接改这三个 visibility。
         */
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

        /**
         * 工具栏与排序模式；空组时 toolbar [ItemGroupBinding.btnAdd] 恒 GONE（加号入口在 empty_layout）。
         */
        private fun syncChromeVisibility() {
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

    private class GroupDiffCallback : DiffUtil.ItemCallback<SnapGroup>() {
        override fun areItemsTheSame(oldItem: SnapGroup, newItem: SnapGroup) = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: SnapGroup, newItem: SnapGroup): Boolean {
            if (oldItem.name != newItem.name) return false
            if (oldItem.isCollapsed != newItem.isCollapsed) return false
            val oldPkgs = oldItem.apps.map { it.appInfo.packageName }
            val newPkgs = newItem.apps.map { it.appInfo.packageName }
            return oldPkgs == newPkgs
        }
    }
}

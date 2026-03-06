package tiiehenry.android.app.snapshotor.main.launch

import android.app.AlertDialog
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import tiiehenry.android.app.snapshotor.R
import tiiehenry.android.app.snapshotor.SnapShotApp
import tiiehenry.android.app.snapshotor.config.SortConfig
import tiiehenry.android.app.snapshotor.databinding.ItemGroupBinding
import tiiehenry.android.app.snapshotor.group.SelectAppFragment
import tiiehenry.android.app.snapshotor.group.SnapGroup
import tiiehenry.android.app.snapshotor.group.SnapedApp
import tiiehenry.android.app.snapshotor.ui.group.GroupConfigFragment

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

        private fun showEditGroupDialog(group: SnapGroup) {
            val context = binding.root.context
            val input = android.widget.EditText(context)
            // 使用应用名称作为默认值
            input.setText(group.name)

            AlertDialog.Builder(context)
                .setTitle("编辑组名称")
                .setView(input)
                .setPositiveButton("确定") { _, _ ->
                    val newName = input.text.toString().trim()
                    if (newName.isNotEmpty()) {
                        group.name = newName
                    }
                    binding.groupTitle.text = group.name
                }
                .setNegativeButton("取消", null)
                .show()
        }

        fun addNewApp(group: SnapGroup) {
            // 显示SelectAppFragment选择应用
            SelectAppFragment.newInstance(group.id) { appInfos ->
                SnapShotApp.getViewModel().addAppsToGroup(group.id, appInfos) {
                    // 从ViewModel获取最新的group对象，确保使用的是已更新的实例
                    val updatedGroup =
                        SnapShotApp.getViewModel().groupList.value?.find { it.id == group.id }
                    if (updatedGroup != null) {
                        refresh(updatedGroup, binding.groupRecyclerView)
                    } else {
                        refresh(group, binding.groupRecyclerView)
                    }
                }
            }.show(fragmentManager, "SelectAppFragment")
        }

        fun bind(groupsAdapter: GroupsAdapter, group: SnapGroup) {
            binding.groupTitle.text = group.name

            // 初始化折叠状态
            updateCollapseState(group.isCollapsed)

            binding.groupTitle.setOnClickListener {
                // 点击标题切换折叠状态
                group.isCollapsed = !group.isCollapsed
                updateCollapseState(group.isCollapsed)
            }
            binding.groupTitle.setOnLongClickListener {
                showEditGroupDialog(group)
                true
            }

            binding.expandGroup.setOnClickListener {
                // 点击展开区域展开分组
                group.isCollapsed = false
                updateCollapseState(group.isCollapsed)
            }

            // 设置GridLayout，4列
            binding.groupRecyclerView.layoutManager = GridLayoutManager(binding.root.context, 4)

            val adapter = GroupItemAdapter(this, groupsAdapter, viewModel, group) { adapter, item ->
                // 当存档被删除后，更新列表
                val currentList = ArrayList(group.apps)
                val index =
                    currentList.indexOfFirst { it.appInfo.packageName == item.appInfo.packageName }
                if (index != -1) {
                    currentList[index] = item
                    adapter.submitList(currentList)
                }
            }
            binding.groupRecyclerView.adapter = adapter
            binding.btnRefresh.setOnClickListener {
                viewModel.viewModelScope.launch {
                    group.loadApps(
                        SnapShotApp.getInstance().fileSystem,
                        SnapShotApp.getInstance().appManager,
                        true
                    )
                    withContext(Dispatchers.Main) {
                        // 控制加载状态
                        refresh(group, binding.groupRecyclerView)
                    }
                }
            }
            refresh(group, binding.groupRecyclerView)

            binding.btnAdd.setOnClickListener {
                addNewApp(group)
            }

            binding.emptyLayout.setOnClickListener {
                addNewApp(group)
            }

            // 根据排序类型决定是否显示btnMove按钮
            val sortType = group.config.sortConfig.sortType
            if (sortType == SortConfig.SORT_TYPE_CUSTOM) { // 自定义排序模式下显示btnMove
                binding.btnMove.visibility = View.VISIBLE
                binding.btnMove.setOnClickListener {
                    // 进入排序状态
                    toggleSortMode(group, adapter)
                }
            } else {
                binding.btnMove.visibility = View.GONE
            }

            binding.btnTune.setOnClickListener {
                // 显示GroupConfigFragment，保存后刷新列表
                GroupConfigFragment.newInstance(group) {
                    refresh(group, binding.groupRecyclerView)
                }.show(fragmentManager, "GroupConfigFragment")
            }

            updateButtonVisibility(!isSortMode)
        }

        private fun toggleSortMode(group: SnapGroup, adapter: GroupItemAdapter) {
            isSortMode = !isSortMode
            if (isSortMode) {
                // 进入排序模式
                binding.btnMove.setImageResource(R.drawable.check) // 使用确认图标表示正在排序
                startDragSortMode(adapter, group)
                // 也可以考虑添加一些视觉提示表明现在是排序模式
                binding.groupTitle.text = "${group.name} (排序模式)"
                // 在排序模式下隐藏其他按钮
                updateButtonVisibility(false)
            } else {
                // 退出排序模式
                binding.btnMove.setImageResource(R.drawable.app_sort) // 恢复原始图标
                binding.groupTitle.text = group.name // 恢复正常标题
                stopDragSortMode(adapter)
                // 恢复按钮显示
                updateButtonVisibility(true)
            }
        }

        private fun updateButtonVisibility(show: Boolean) {
            binding.btnAdd.visibility = if (show) View.VISIBLE else View.GONE
            binding.btnTune.visibility = if (show) View.VISIBLE else View.GONE
            binding.btnRefresh.visibility = if (show) View.VISIBLE else View.GONE
        }


        private fun startDragSortMode(adapter: GroupItemAdapter, group: SnapGroup) {
            val callback = object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
                0
            ) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    val fromPosition = viewHolder.adapterPosition
                    val toPosition = target.adapterPosition

                    // 交换数据
                    val currentList = adapter.currentList.toMutableList()
                    val item = currentList[fromPosition]
                    currentList.removeAt(fromPosition)
                    currentList.add(toPosition, item)

                    // 更新适配器
                    adapter.submitList(currentList)

                    // 立即保存排序后的顺序（submitList是异步的，currentList可能还未更新）
                    saveSortOrderToConfig(currentList, group)

                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    // 不处理滑动删除
                }

                override fun isLongPressDragEnabled(): Boolean {
                    return false // 禁用长按拖拽，我们通过触摸事件控制
                }

                override fun isItemViewSwipeEnabled(): Boolean {
                    return false // 禁用滑动
                }

                override fun onMoved(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    fromPos: Int,
                    target: RecyclerView.ViewHolder,
                    toPos: Int,
                    x: Int,
                    y: Int
                ) {
                    super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y)
                    // 排序已在 onMove 中保存，这里可以添加其他拖拽完成的逻辑
                }
            }

            itemTouchHelper = ItemTouchHelper(callback)
            itemTouchHelper?.attachToRecyclerView(binding.groupRecyclerView)

            // 将 ItemTouchHelper 设置给 adapter，以便在 onBindViewHolder 中使用
            adapter.itemTouchHelper = itemTouchHelper
            // 刷新列表以应用拖拽触摸监听
            adapter.notifyDataSetChanged()
        }

        private fun stopDragSortMode(adapter: GroupItemAdapter) {
            itemTouchHelper?.attachToRecyclerView(null)
            itemTouchHelper = null
            // 清除 adapter 的 ItemTouchHelper 引用
            adapter.itemTouchHelper = null
            // 刷新列表以清除拖拽触摸监听
            adapter.notifyDataSetChanged()
        }

        private fun saveSortOrderToConfig(sortedList: List<SnapedApp>, group: SnapGroup) {
            // 获取排序后的包名列表
            val sortedPackageNames = sortedList.map { it.appInfo.packageName }

            // 保存到SortConfig
            val sortConfig = group.config.sortConfig
            sortConfig.sortOrder = sortedPackageNames.toMutableList()

            // 也可以设置排序类型为自定义
            sortConfig.sortType = SortConfig.SORT_TYPE_CUSTOM

            // 保存所有配置到文件
            group.config.save()
        }

        fun refresh(
            group: SnapGroup,
            recyclerView: RecyclerView
        ) {
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
                // 应用排序
                applySorting(group.apps, group.config.sortConfig, group)

            }
            Log.i("GroupsAdapter", "refresh " + sortedApps)
            (recyclerView.adapter as GroupItemAdapter).submitList(sortedApps)
            (recyclerView.adapter as GroupItemAdapter).notifyDataSetChanged()
            recyclerView.invalidate()
            recyclerView.requestLayout()

            // 更新折叠状态（保留当前折叠状态）
            updateCollapseState(group.isCollapsed)
        }

        private fun updateCollapseState(isCollapsed: Boolean) {
            if (isCollapsed) {
                // 折叠状态下：隐藏app_layout，显示expand_group
                binding.appLayout.visibility = View.GONE
                binding.expandGroup.visibility = View.VISIBLE
            } else {
                // 展开状态下：显示app_layout，隐藏expand_group
                binding.appLayout.visibility = View.VISIBLE
                binding.expandGroup.visibility = View.GONE
            }
        }

        private fun applySorting(
            apps: List<SnapedApp>,
            sortConfig: SortConfig,
            group: SnapGroup
        ): List<SnapedApp> {
            binding.btnMove.visibility =
                if (sortConfig.sortType == SortConfig.SORT_TYPE_CUSTOM) View.VISIBLE else View.GONE

            return when (sortConfig.sortType) {
                SortConfig.SORT_TYPE_CUSTOM -> { // 自定义排序
                    val sortOrder = sortConfig.sortOrder.toMutableList()
                    val appPackageNames = apps.map { it.appInfo.packageName }

                    // 1. 移除 sortOrder 中已不存在的应用（被删除的）
                    sortOrder.removeAll { it !in appPackageNames }

                    // 2. 将新添加的应用（不在 sortOrder 中的）添加到末尾
                    val newApps = appPackageNames.filter { it !in sortOrder }
                    sortOrder.addAll(newApps)

                    // 3. 如果 sortOrder 有变化，保存更新后的配置
                    if (sortOrder != sortConfig.sortOrder) {
                        sortConfig.sortOrder = sortOrder
                        group.config.save()
                    }

                    // 4. 按 sortOrder 排序应用
                    apps.sortedBy { sortOrder.indexOf(it.appInfo.packageName) }
                }

                SortConfig.SORT_TYPE_NAME_DESC -> { // 按名称排序（降序）
                    apps.sortedByDescending { it.appInfo.label }
                }

                SortConfig.SORT_TYPE_NAME_ASC -> { // 按名称排序（升序）
                    apps.sortedBy { it.appInfo.label }
                }

                else -> { // 默认排序
                    apps.toList()
                }
            }
        }
    }

    private class GroupDiffCallback : DiffUtil.ItemCallback<SnapGroup>() {
        override fun areItemsTheSame(oldItem: SnapGroup, newItem: SnapGroup): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SnapGroup, newItem: SnapGroup): Boolean {
            return oldItem == newItem
        }
    }
}
package tiiehenry.android.app.snapshot.main.launch

import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.SnapshotViewModel
import tiiehenry.android.app.snapshot.config.SortConfig
import tiiehenry.android.app.snapshot.databinding.ItemGroupBinding
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.main.launch.group.GroupConfigFragment
import tiiehenry.android.app.snapshot.main.launch.group.GroupSettingFragment
import tiiehenry.android.app.snapshot.main.selectapp.SelectAppFragment

/**
 * GroupViewHolder 的动作控制器
 * 负责处理按钮点击、导航、批量操作等 UI 事件
 */
class GroupActionsController(
    private val binding: ItemGroupBinding,
    private val viewModel: LauncherViewModel,
    private val snapshotViewModel: SnapshotViewModel,
    private val fragmentManager: androidx.fragment.app.FragmentManager,
    private val onRefresh: (SnapGroup) -> Unit
) {

    private var archiver: GroupBatchArchiver? = null

    fun setupActions(group: SnapGroup, groupsAdapter: GroupsAdapter, groupViewHolder: GroupsAdapter.GroupViewHolder) {
        archiver = GroupBatchArchiver(binding.root.context, viewModel.viewModelScope) { g ->
            onRefresh(g)
        }

        // 标题点击 - 折叠/展开
        binding.groupTitle.setOnClickListener {
            group.isCollapsed = !group.isCollapsed
            groupViewHolder.updateCollapseState(group.isCollapsed)
        }

        // 标题长按 - 打开分组设置
        binding.groupTitle.setOnLongClickListener {
            GroupSettingFragment.newInstance(group) {
                onRefresh(group)
            }.show(fragmentManager, "GroupConfigFragment")
            true
        }

        // 展开按钮
        binding.expandGroup.setOnClickListener {
            group.isCollapsed = false
            groupViewHolder.updateCollapseState(group.isCollapsed)
        }

        // 刷新按钮
        binding.btnRefresh.setOnClickListener {
            viewModel.viewModelScope.launch {
                val app = SnapshotApp.getInstance()
                group.loadApps(
                    SnapshotApp.getContext(),
                    app.fileSystem,
                    app.appManager,
                    reload = true
                )
                withContext(Dispatchers.Main) { onRefresh(group) }
            }
        }
        binding.btnRefresh.setOnLongClickListener {
            archiver?.showGroupStatistics(group)
            true
        }

        // 添加应用按钮
        binding.btnAdd.setOnClickListener {
            SelectAppFragment.newInstance(group.id) { appInfos ->
                snapshotViewModel.addAppsToGroup(group.id, appInfos) {
                    val updatedGroup = snapshotViewModel.groupList.value?.find { it.id == group.id }
                    onRefresh(updatedGroup ?: group)
                }
            }.show(fragmentManager, "SelectAppFragment")
        }

        // 排序按钮
        binding.btnMove.setOnClickListener { v -> showSortTypePopupMenu(v, group, groupViewHolder) }
        if (group.config.sortConfig.sortType == SortConfig.SORT_TYPE_CUSTOM) {
            binding.btnMove.setOnLongClickListener {
                groupViewHolder.toggleSortMode(group, binding.groupRecyclerView.adapter as GroupItemAdapter)
                true
            }
        }

        // 配置按钮
        binding.btnTune.setOnClickListener {
            GroupConfigFragment.newInstance(group) {
                onRefresh(group)
            }.show(fragmentManager, "GroupShotConfigFragment")
        }

        // 一键存档按钮
        binding.btnArchiveAll.setOnClickListener {
            archiver?.archiveAllApps(group)
        }
    }

    private fun showSortTypePopupMenu(
        anchor: View,
        group: SnapGroup,
        groupViewHolder: GroupsAdapter.GroupViewHolder
    ) {
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
                    val adapter = binding.groupRecyclerView.adapter as GroupItemAdapter
                    groupViewHolder.toggleSortMode(group, adapter)
                    true
                }
            } else {
                binding.btnMove.setOnLongClickListener(null)
                if (groupViewHolder.isSortMode) {
                    val adapter = binding.groupRecyclerView.adapter as GroupItemAdapter
                    groupViewHolder.toggleSortMode(group, adapter)
                }
            }
            onRefresh(group)
            true
        }
        popup.show()
    }

    fun updateButtonVisibility(show: Boolean) {
        binding.btnMove.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnAdd.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnTune.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnRefresh.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnArchiveAll.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnConfirm.visibility = if (show) View.GONE else View.VISIBLE
    }
}

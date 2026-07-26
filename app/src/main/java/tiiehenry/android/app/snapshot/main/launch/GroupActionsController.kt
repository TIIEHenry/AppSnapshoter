package tiiehenry.android.app.snapshot.main.launch

import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.SnapshotViewModel
import tiiehenry.android.app.snapshot.config.SortConfig
import tiiehenry.android.app.snapshot.databinding.ItemGroupBinding
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.main.launch.batch.GroupBatchRestoreDialog
import tiiehenry.android.app.snapshot.main.launch.batch.GroupBatchRestorer
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
    private var restorer: GroupBatchRestorer? = null

    private fun resolveGroup(fallback: SnapGroup): SnapGroup =
        snapshotViewModel.resolveGroup(fallback.id, fallback) ?: fallback

    fun setupActions(group: SnapGroup, groupsAdapter: GroupsAdapter, groupViewHolder: GroupsAdapter.GroupViewHolder) {
        archiver = GroupBatchArchiver(binding.root.context, viewModel.viewModelScope, snapshotViewModel) { g ->
            onRefresh(g)
        }
        restorer = GroupBatchRestorer(binding.root.context, viewModel.viewModelScope, snapshotViewModel) { g ->
            onRefresh(g)
        }

        // 标题点击 - 折叠/展开
        binding.groupTitle.setOnClickListener {
            if (groupsAdapter.isBatchRunning) return@setOnClickListener
            group.isCollapsed = !group.isCollapsed
            groupViewHolder.updateCollapseState(group.isCollapsed)
        }

        // 标题长按 - 打开分组设置
        binding.groupTitle.setOnLongClickListener {
            if (groupsAdapter.isBatchRunning) return@setOnLongClickListener true
            GroupSettingFragment.newInstance(group) {
                onRefresh(resolveGroup(group))
            }.show(fragmentManager, "GroupConfigFragment")
            true
        }

        // 展开按钮
        binding.expandGroup.setOnClickListener {
            if (groupsAdapter.isBatchRunning) return@setOnClickListener
            group.isCollapsed = false
            groupViewHolder.updateCollapseState(group.isCollapsed)
        }

        // 刷新按钮
        binding.btnRefresh.setOnClickListener {
            if (groupsAdapter.isBatchRunning) return@setOnClickListener
            viewModel.viewModelScope.launch {
                val app = SnapshotApp.getInstance()
                val current = resolveGroup(group)
                current.loadApps(
                    SnapshotApp.getContext(),
                    app.fileSystem,
                    app.appManager,
                    reload = true
                )
                withContext(Dispatchers.Main) { onRefresh(resolveGroup(current)) }
            }
        }
        binding.btnRefresh.setOnLongClickListener {
            if (groupsAdapter.isBatchRunning) return@setOnLongClickListener true
            archiver?.showGroupStatistics(resolveGroup(group))
            true
        }

        // 添加应用按钮
        binding.btnAdd.setOnClickListener {
            if (groupsAdapter.isBatchRunning) return@setOnClickListener
            SelectAppFragment.newInstance(group.id) { appInfos ->
                snapshotViewModel.addAppsToGroup(group.id, appInfos) {
                    onRefresh(resolveGroup(group))
                }
            }.show(fragmentManager, "SelectAppFragment")
        }

        // 排序按钮
        binding.btnMove.setOnClickListener { v ->
            if (groupsAdapter.isBatchRunning) return@setOnClickListener
            showSortTypePopupMenu(v, group, groupViewHolder)
        }
        if (group.config.sortConfig.sortType == SortConfig.SORT_TYPE_CUSTOM) {
            binding.btnMove.setOnLongClickListener {
                if (groupsAdapter.isBatchRunning) return@setOnLongClickListener true
                groupViewHolder.toggleSortMode(group, binding.groupRecyclerView.adapter as GroupItemAdapter)
                true
            }
        }

        // 配置按钮
        binding.btnTune.setOnClickListener {
            if (groupsAdapter.isBatchRunning) return@setOnClickListener
            GroupConfigFragment.newInstance(group) {
                onRefresh(resolveGroup(group))
            }.show(fragmentManager, "GroupShotConfigFragment")
        }

        // 批量操作菜单
        binding.btnBatch.setOnClickListener { anchor ->
            if (groupsAdapter.isBatchRunning) {
                Toast.makeText(anchor.context, R.string.batch_operation_in_progress, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            PopupMenu(anchor.context, anchor).apply {
                menu.add(0, R.id.menu_batch_archive, 0, R.string.group_batch_menu_archive)
                menu.add(0, R.id.menu_batch_restore, 1, R.string.group_batch_menu_restore)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.menu_batch_archive -> archiver?.archiveAllApps(resolveGroup(group))
                        R.id.menu_batch_restore -> {
                            val current = resolveGroup(group)
                            GroupBatchRestoreDialog.show(anchor.context, current) { tasks ->
                                restorer?.execute(current, tasks)
                            }
                        }
                    }
                    true
                }
                show()
            }
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
            SortConfig.SORT_TYPE_DEFAULT to anchor.context.getString(R.string.sort_default),
            SortConfig.SORT_TYPE_NAME_ASC to anchor.context.getString(R.string.sort_name_asc),
            SortConfig.SORT_TYPE_NAME_DESC to anchor.context.getString(R.string.sort_name_desc),
            SortConfig.SORT_TYPE_INSTALL_TIME_ASC to anchor.context.getString(R.string.sort_install_time_asc),
            SortConfig.SORT_TYPE_INSTALL_TIME_DESC to anchor.context.getString(R.string.sort_install_time_desc),
            SortConfig.SORT_TYPE_CUSTOM to anchor.context.getString(R.string.sort_custom)
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
            onRefresh(resolveGroup(group))
            true
        }
        popup.show()
    }

    fun updateButtonVisibility(show: Boolean, isEmpty: Boolean = false) {
        binding.btnMove.visibility = if (show) View.VISIBLE else View.GONE
        // 空组加号入口在 empty_layout，toolbar btnAdd 必须 GONE，避免双加号
        binding.btnAdd.visibility = if (show && !isEmpty) View.VISIBLE else View.GONE
        binding.btnTune.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnRefresh.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnBatch.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnConfirm.visibility = if (show) View.GONE else View.VISIBLE
    }

    fun setBatchRunning(running: Boolean) {
        binding.btnBatch.isEnabled = !running
        binding.btnRefresh.isEnabled = !running
        binding.btnAdd.isEnabled = !running
        binding.btnMove.isEnabled = !running
        binding.btnTune.isEnabled = !running
        binding.root.alpha = if (running) 0.7f else 1f
    }
}

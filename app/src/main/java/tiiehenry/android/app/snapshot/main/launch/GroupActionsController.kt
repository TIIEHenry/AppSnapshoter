package tiiehenry.android.app.snapshot.main.launch

import android.app.AlertDialog
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
import tiiehenry.android.app.snapshot.group.AddAppItemResult
import tiiehenry.android.app.snapshot.group.AddAppsResult
import tiiehenry.android.app.snapshot.group.MoveAppResult
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
            notifyRefreshed(g)
        }
        restorer = GroupBatchRestorer(binding.root.context, viewModel.viewModelScope, snapshotViewModel) { g ->
            notifyRefreshed(g)
        }

        // 标题点击 - 有应用时折叠/展开；空组始终显示加号，不切换折叠。
        // 有查询时不写 MMKV、不改 displayCollapsed。
        binding.groupTitle.setOnClickListener {
            if (groupsAdapter.isBatchRunning) return@setOnClickListener
            if (synchronized(group.apps) { group.apps.isEmpty() }) return@setOnClickListener
            if (groupsAdapter.searchQuery.isNotBlank()) return@setOnClickListener
            group.isCollapsed = !group.isCollapsed
            groupViewHolder.updateCollapseState(group.isCollapsed)
        }

        // 标题长按 - 打开分组设置
        binding.groupTitle.setOnLongClickListener {
            if (groupsAdapter.isBatchRunning) return@setOnLongClickListener true
            GroupSettingFragment.newInstance(group) {
                notifyRefreshed(resolveGroup(group))
            }.show(fragmentManager, "GroupConfigFragment")
            true
        }

        // 展开按钮
        binding.expandGroup.setOnClickListener {
            if (groupsAdapter.isBatchRunning) return@setOnClickListener
            if (groupsAdapter.searchQuery.isNotBlank()) return@setOnClickListener
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
                withContext(Dispatchers.Main) { notifyRefreshed(resolveGroup(current)) }
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
            val targetGroupId = group.id
            SelectAppFragment.newInstance(targetGroupId) { appInfos ->
                snapshotViewModel.addAppsToGroup(targetGroupId, appInfos) { result ->
                    notifyRefreshed(resolveGroup(group))
                    handleAddAppsResult(targetGroupId, result)
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
                notifyRefreshed(resolveGroup(group))
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
            notifyRefreshed(resolveGroup(group))
            true
        }
        popup.show()
    }

    private fun notifyRefreshed(group: SnapGroup) {
        onRefresh(group)
        if (viewModel.isSearching) {
            viewModel.rematerializeDisplayed()
        }
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

    private fun handleAddAppsResult(targetGroupId: String, result: AddAppsResult) {
        val context = binding.root.context
        val conflicts = result.conflicts
        if (conflicts.isEmpty()) {
            val busy = result.items.values.any { it is AddAppItemResult.Busy }
            val corrupt = result.items.values.any { it is AddAppItemResult.CorruptMultiOwner }
            when {
                corrupt -> Toast.makeText(
                    context, R.string.group_membership_corrupt, Toast.LENGTH_LONG
                ).show()
                busy -> Toast.makeText(
                    context, R.string.batch_operation_in_progress, Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        val target = snapshotViewModel.resolveGroup(targetGroupId) ?: return
        if (conflicts.size == 1) {
            val (pkg, ownerId) = conflicts.entries.first()
            val owner = snapshotViewModel.resolveGroup(ownerId)
            val ownerName = owner?.name ?: ownerId
            AlertDialog.Builder(context)
                .setTitle(R.string.group_move_conflict_title)
                .setMessage(
                    context.getString(
                        R.string.group_move_conflict_message,
                        pkg,
                        ownerName,
                        target.name
                    )
                )
                .setPositiveButton(R.string.group_move_action) { _, _ ->
                    moveApps(mapOf(pkg to ownerId), targetGroupId)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            val lines = conflicts.entries.joinToString("\n") { (pkg, ownerId) ->
                val ownerName = snapshotViewModel.resolveGroup(ownerId)?.name ?: ownerId
                "$pkg → $ownerName"
            }
            AlertDialog.Builder(context)
                .setTitle(R.string.group_move_conflict_title)
                .setMessage(
                    context.getString(R.string.group_move_conflict_multi_message, lines)
                )
                .setPositiveButton(R.string.group_move_all_action) { _, _ ->
                    moveApps(conflicts, targetGroupId)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun moveApps(conflicts: Map<String, String>, targetGroupId: String) {
        val context = binding.root.context
        val entries = conflicts.entries.toList()
        fun moveNext(index: Int) {
            if (index >= entries.size) {
                val target = snapshotViewModel.resolveGroup(targetGroupId)
                if (target != null) notifyRefreshed(target)
                return
            }
            val (pkg, fromId) = entries[index]
            snapshotViewModel.moveAppBetweenGroups(fromId, targetGroupId, pkg) { result ->
                when (result) {
                    is MoveAppResult.Moved, is MoveAppResult.AlreadyAtTarget -> {
                        moveNext(index + 1)
                    }
                    is MoveAppResult.Busy -> {
                        Toast.makeText(
                            context, R.string.batch_operation_in_progress, Toast.LENGTH_SHORT
                        ).show()
                    }
                    is MoveAppResult.Locked -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.group_move_failed_locked, pkg),
                            Toast.LENGTH_LONG
                        ).show()
                        moveNext(index + 1)
                    }
                    is MoveAppResult.TargetNonEmpty -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.group_move_failed_target_nonempty, pkg),
                            Toast.LENGTH_LONG
                        ).show()
                        moveNext(index + 1)
                    }
                    is MoveAppResult.CorruptMultiOwner -> {
                        Toast.makeText(
                            context, R.string.group_membership_corrupt, Toast.LENGTH_LONG
                        ).show()
                        moveNext(index + 1)
                    }
                    is MoveAppResult.Error -> {
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.group_move_failed_generic,
                                pkg,
                                result.message
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                        moveNext(index + 1)
                    }
                }
            }
        }
        moveNext(0)
    }
}

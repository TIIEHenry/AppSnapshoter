package tiiehenry.android.app.snapshot.main.launch

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.data.ArchiveManager
import tiiehenry.android.app.snapshot.data.SnapshotCreator
import tiiehenry.android.app.snapshot.databinding.ItemAppBinding
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.group.SnapedApp
import tiiehenry.android.app.snapshot.ui.ArchiveItemPopupMenu
import tiiehenry.android.app.snapshot.util.AppStatusHelper


class GroupItemAdapter(
    private val groupsHolder: GroupsAdapter.GroupViewHolder,
    private val groupsAdapter: GroupsAdapter,
    private val viewModel: LauncherViewModel,
    private val group: SnapGroup,
    private val onItemUpdated: (GroupItemAdapter, SnapedApp) -> Unit = { a, s -> } // 添加更新回调){}){}
) : ListAdapter<SnapedApp, GroupItemAdapter.ViewHolder>(ItemDiffCallback()) {

    // ItemTouchHelper 引用，用于拖拽排序
    var itemTouchHelper: ItemTouchHelper? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, groupsHolder, viewModel, group, onItemUpdated, this)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
        // 在排序模式下设置触摸监听以启动拖拽
        if (groupsHolder.isSortMode) {
            holder.setupDragOnTouch()
        } else {
            holder.clearDragOnTouch()
        }
    }

    class ViewHolder(
        private val binding: ItemAppBinding,
        private val groupsHolder: GroupsAdapter.GroupViewHolder,
        private val viewModel: LauncherViewModel,
        private val group: SnapGroup,
        private val onItemUpdated: (GroupItemAdapter, SnapedApp) -> Unit,
        private val adapter: GroupItemAdapter
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SnapedApp) {
            val appInfo = item.appInfo
            binding.appName.text = appInfo.label

            // 使用Glide加载图标
            Glide.with(binding.root.context)
                .load(appInfo)
                .into(binding.appIcon)

            // 根据应用状态设置状态指示器
            updateStatusIndicator(item)

            // 根据应用是否正在运行显示运行指示器
            updateRunningIndicator(item)

            // 根据应用是否在锁定列表中显示锁图标
            updateLockIndicator(item)

            binding.root.setOnClickListener {
                // 排序模式下禁用点击
                if (groupsHolder.isSortMode) {
                    return@setOnClickListener
                }

                // 检查应用是否已安装
                if (AppStatusHelper.isAppInstalled(item)) {
                    // 应用已安装，启动应用
                    launchApp(item.appInfo.packageName, item.appInfo.userId)
                } else {
                    // 应用未安装，执行备份/恢复逻辑
                    viewModel.onGroupItemClicked(
                        binding.root.context,
                        group.id,
                        group.mmkv,
                        appInfo.packageName,
                        item
                    )
                }
            }

            binding.root.setOnLongClickListener {
                // 只有在非排序模式下才显示弹出菜单
                if (!groupsHolder.isSortMode) {
                    showPopupMenu(item)
                }
                !groupsHolder.isSortMode // 如果是排序模式，不消费长按事件
            }
        }

        private fun updateStatusIndicator(item: SnapedApp) {
            // 判断应用状态
            val status = AppStatusHelper.getPackageStatus(item)

            // 根据状态设置图标和可见性
            binding.appStatusIndicator.visibility = android.view.View.VISIBLE
            val iconRes = when (status) {
                tiiehenry.android.app.snapshot.model.PackageStatus.NOT_INSTALLED -> R.drawable.ic_status_not_installed
                tiiehenry.android.app.snapshot.model.PackageStatus.INSTALLED -> R.drawable.ic_status_installed
                tiiehenry.android.app.snapshot.model.PackageStatus.CAN_UPDATE -> R.drawable.ic_status_can_update
            }
            binding.appStatusIndicator.setImageResource(iconRes)
        }

        /**
         * 更新运行状态指示器
         */
        private fun updateRunningIndicator(item: SnapedApp) {
            binding.appRunIndicator.visibility = if (item.isRunning) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }

        /**
         * 更新锁定状态指示器
         */
        private fun updateLockIndicator(item: SnapedApp) {
            val isLocked = group.config.isLocked(item.appInfo.packageName)
            binding.appLockIndicator.visibility = if (isLocked) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }

        // 设置拖拽触摸监听
        fun setupDragOnTouch() {
            binding.root.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    adapter.itemTouchHelper?.startDrag(this)
                    true
                } else {
                    false
                }
            }
        }

        // 清除拖拽触摸监听
        fun clearDragOnTouch() {
            binding.root.setOnTouchListener(null)
        }

        private fun showPopupMenu(item: SnapedApp) {
            val archiveItemPopupMenu = ArchiveItemPopupMenu(
                binding.root.context,
                groupsHolder.fragmentManager,
                viewModel.viewModelScope
            )

            archiveItemPopupMenu.showPopupMenu(
                anchor = binding.root,
                item = item,
                group = group,
                callback = createPopupMenuCallback(item)
            )
        }

        private fun createPopupMenuCallback(item: SnapedApp): ArchiveItemPopupMenu.Callback {
            return object : ArchiveItemPopupMenu.Callback {
                override fun onArchiveItemClick(
                    item: SnapedApp,
                    archiveItem: ArchiveItem,
                    needConfirm: Boolean
                ) {
                    if (needConfirm) {
                        showRestoreConfirmDialog(item, archiveItem)
                    } else {
                        viewModel.onGroupItemClicked(
                            binding.root.context,
                            group.id,
                            group.mmkv,
                            item.appInfo.packageName,
                            item
                        )
                    }
                }

                override fun onAdvancedRestoreClick(
                    item: SnapedApp,
                    archiveItem: ArchiveItem,
                    selectedTypes: Set<String>
                ) {
                    viewModel.onAdvancedRestoreClicked(
                        binding.root.context,
                        item,
                        archiveItem,
                        selectedTypes
                    )
                }

                override fun onCreateSnapshot(item: SnapedApp) {
                    // 检查应用是否已安装
                    if (!AppStatusHelper.isAppInstalled(item)) {
                        Toast.makeText(binding.root.context, "应用未安装，无法创建快照", Toast.LENGTH_SHORT).show()
                        return
                    }
                    createSnapshot(item)
                }

                override fun onClearAllArchives(item: SnapedApp, onComplete: () -> Unit) {
                    clearAllArchives(item, onComplete)
                }

                override fun onDeleteApp(item: SnapedApp, onComplete: () -> Unit) {
                    deleteAppCompletely(item, onComplete)
                }

                override fun onLockStateChanged(item: SnapedApp, isLocked: Boolean) {
                    // 锁定状态变化时，重新加载组数据以刷新 UI
                    groupsHolder.refresh(group, groupsHolder.binding.groupRecyclerView)
                    SnapshotApp.getViewModel().loadGroups()
                }
            }
        }

        private fun showRestoreConfirmDialog(item: SnapedApp, archiveItem: ArchiveItem) {
            androidx.appcompat.app.AlertDialog.Builder(binding.root.context)
                .setTitle("确认操作")
                .setMessage("确定要恢复存档 '${archiveItem.name}' 吗？")
//                .setNeutralButton() todo 点击删除存档
                .setPositiveButton("确认") { _, _ ->
                    viewModel.onGroupItemClicked(
                        binding.root.context,
                        group.id,
                        group.mmkv,
                        item.appInfo.packageName,
                        item
                    )
                }
                .setNegativeButton("取消", null)
                .show()
        }

        private fun clearAllArchives(item: SnapedApp, onComplete: () -> Unit) {
            viewModel.viewModelScope.launch {
                val success = ArchiveManager.clearAllArchives(item)
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(binding.root.context, "删除存档成功", Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        Toast.makeText(binding.root.context, "删除失败", Toast.LENGTH_SHORT).show()
                    }
                    ArchiveManager.reloadArchives(item, true)
                    onComplete()
                }
            }
        }

        private fun deleteAppCompletely(item: SnapedApp, onComplete: () -> Unit) {
            viewModel.viewModelScope.launch {
                val success = ArchiveManager.deleteAppCompletely(item, group)
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(binding.root.context, "删除应用成功", Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        Toast.makeText(binding.root.context, "删除失败", Toast.LENGTH_SHORT).show()
                    }
                    // 从组中移除应用并刷新 UI
                    group.apps.remove(item)
                    groupsHolder.refresh(group, groupsHolder.binding.groupRecyclerView)
                    // 通知全局 ViewModel 更新数据
                    SnapshotApp.getViewModel().loadGroups()
                    onComplete()
                }
            }
        }

        private fun launchApp(packageName: String, userId: Int) {
            val context = binding.root.context
            val success = AppStatusHelper.launchApp(packageName, userId)
            if (!success) {
                Toast.makeText(context, "无法启动应用", Toast.LENGTH_SHORT).show()
            }
        }

        /**
         * 创建应用快照
         */
        private fun createSnapshot(item: SnapedApp) {
            val snapshotCreator = SnapshotCreator(binding.root.context, viewModel.viewModelScope)
            snapshotCreator.createSnapshot(item, group, object : SnapshotCreator.Callback {
                override fun onSuccess() {
                    // 重新加载应用数据
                    groupsHolder.refresh(group, groupsHolder.binding.groupRecyclerView)
                    SnapshotApp.getViewModel().loadGroups()
                }

                override fun onError(message: String) {
                    // 错误已在 SnapshotCreator 中处理（显示Toast）
                }
            })
        }
    }

    private class ItemDiffCallback : DiffUtil.ItemCallback<SnapedApp>() {
        override fun areItemsTheSame(oldItem: SnapedApp, newItem: SnapedApp): Boolean {
            return oldItem.packageDir == newItem.packageDir
        }

        override fun areContentsTheSame(oldItem: SnapedApp, newItem: SnapedApp): Boolean {
            return oldItem == newItem
        }
    }
}
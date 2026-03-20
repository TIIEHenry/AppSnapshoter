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
import tiiehenry.android.app.snapshot.model.PackageStatus
import tiiehenry.android.app.snapshot.ui.ArchiveItemPopupMenu
import tiiehenry.android.app.snapshot.util.AppStatusHelper

/**
 * 分组应用列表适配器
 * @param groupsHolder 分组 ViewHolder
 * @param groupsAdapter 分组适配器
 * @param viewModel ViewModel
 * @param group 所属分组
 * @param onItemUpdated 项目更新回调
 */
class GroupItemAdapter(
    private val groupsHolder: GroupsAdapter.GroupViewHolder,
    private val groupsAdapter: GroupsAdapter,
    private val viewModel: LauncherViewModel,
    private val group: SnapGroup,
    private val onItemUpdated: (GroupItemAdapter, SnapedApp) -> Unit = { _, _ -> }
) : ListAdapter<SnapedApp, GroupItemAdapter.ViewHolder>(ItemDiffCallback()) {

    var itemTouchHelper: ItemTouchHelper? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, groupsHolder, viewModel, group, onItemUpdated, this)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
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
            loadAppIcon(appInfo)
            updateStatusIndicator(item)
            updateRunningIndicator(item)
            updateLockIndicator(item)
            setupClickListeners(item)
        }

        /**
         * 加载应用图标
         */
        private fun loadAppIcon(appInfo: tiiehenry.android.app.snapshot.app.AppInfo) {
            Glide.with(binding.root.context)
                .load(appInfo)
                .into(binding.appIcon)
        }

        /**
         * 设置点击监听器
         */
        private fun setupClickListeners(item: SnapedApp) {
            binding.root.setOnClickListener { handleItemClick(item) }
            binding.root.setOnLongClickListener { handleItemLongClick(item) }
        }

        /**
         * 处理点击事件
         */
        private fun handleItemClick(item: SnapedApp) {
            if (groupsHolder.isSortMode) return

            if (AppStatusHelper.isAppInstalled(item)) {
                launchApp(item.appInfo.packageName, item.appInfo.userId)
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

        /**
         * 处理长按事件
         */
        private fun handleItemLongClick(item: SnapedApp): Boolean {
            if (!groupsHolder.isSortMode) {
                showPopupMenu(item)
            }
            return !groupsHolder.isSortMode
        }

        private fun updateStatusIndicator(item: SnapedApp) {
            val status = AppStatusHelper.getPackageStatus(item)
            binding.appStatusIndicator.visibility = android.view.View.VISIBLE
            val iconRes = when (status) {
                PackageStatus.NOT_INSTALLED -> R.drawable.ic_status_not_installed
                PackageStatus.INSTALLED -> R.drawable.ic_status_installed
                PackageStatus.CAN_UPDATE -> R.drawable.ic_status_can_update
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
                callback = popupMenuCallback
            )
        }

        val popupMenuCallback = object : ArchiveItemPopupMenu.Callback {
            override fun onArchiveItemClick(
                item: SnapedApp,
                archiveItem: ArchiveItem,
                needConfirm: Boolean,
                archiveAdapter: ArchiveItemAdapter
            ) {
                if (needConfirm) {
                    showRestoreConfirmDialog(item, archiveItem, archiveAdapter)
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

            private fun showRestoreConfirmDialog(
                item: SnapedApp,
                archiveItem: ArchiveItem,
                archiveAdapter: ArchiveItemAdapter
            ) {
                androidx.appcompat.app.AlertDialog.Builder(binding.root.context)
                    .setTitle("确认操作")
                    .setMessage("确定要恢复存档 '${archiveItem.name}' 吗？")
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
                    .setNeutralButton("删除") { _, _ ->
                        deleteArchive(item, archiveItem, archiveAdapter)
                    }
                    .show()
            }

            override fun deleteArchive(
                item: SnapedApp,
                archiveItem: ArchiveItem,
                archiveAdapter: ArchiveItemAdapter
            ) {
                viewModel.viewModelScope.launch {
                    val success = ArchiveManager.deleteArchive(item, archiveItem)
                    if (success) {
                        // 删除成功后从列表中移除该项
                        val currentList = archiveAdapter.currentList.toMutableList()
                        currentList.removeAll { it.name == archiveItem.name }
                        archiveAdapter.submitList(currentList) {
                            archiveAdapter.notifyDataSetChanged()
                        }
                        ArchiveManager.reloadArchives(item, true)
                        Toast.makeText(binding.root.context, "存档删除成功", Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        Toast.makeText(binding.root.context, "删除失败", Toast.LENGTH_SHORT)
                            .show()
                    }
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
                if (!AppStatusHelper.isAppInstalled(item)) {
                    Toast.makeText(
                        binding.root.context,
                        "应用未安装，无法创建快照",
                        Toast.LENGTH_SHORT
                    ).show()
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
                groupsHolder.refresh(group, groupsHolder.binding.groupRecyclerView)
                SnapshotApp.getViewModel().loadGroups()
            }
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
                    group.apps.remove(item)
                    groupsHolder.refresh(group, groupsHolder.binding.groupRecyclerView)
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
                    groupsHolder.refresh(group, groupsHolder.binding.groupRecyclerView)
                    SnapshotApp.getViewModel().loadGroups()
                }

                override fun onError(message: String) {
                    // 错误已在 SnapshotCreator 中处理（显示 Toast）
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
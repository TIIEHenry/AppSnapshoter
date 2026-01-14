package tiieherny.android.app.snapshotor.main.launch

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiieherny.android.app.snapshotor.SnapShotApp
import tiieherny.android.app.snapshotor.app.AppConfigFragment
import tiieherny.android.app.snapshotor.group.SnapGroup
import tiieherny.android.app.snapshotor.group.SnapedApp
import tiieherny.android.app.snapshotor.databinding.ItemAppBinding
import tiieherny.android.app.snapshotor.databinding.LayoutPopupMenuBinding
import tiieherny.android.app.snapshotor.utils.ArchiveRenameHelper
import androidx.core.graphics.drawable.toDrawable

class GroupItemAdapter(
    private val groupsHolder: GroupsAdapter.GroupViewHolder,
    private val groupsAdapter: GroupsAdapter,
    private val viewModel: LauncherViewModel,
    private val group: SnapGroup,
    private val onItemUpdated: (GroupItemAdapter, SnapedApp) -> Unit = { a, s -> } // 添加更新回调){}){}
) : ListAdapter<SnapedApp, GroupItemAdapter.ViewHolder>(ItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding,  groupsHolder, viewModel, group, onItemUpdated)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemAppBinding,
        private val groupsHolder: GroupsAdapter.GroupViewHolder,
        private val viewModel: LauncherViewModel,
        private val group: SnapGroup,
        private val onItemUpdated: (GroupItemAdapter, SnapedApp) -> Unit
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

            binding.root.setOnClickListener {
                viewModel.onGroupItemClicked(group.id, group.mmkv, appInfo.packageName, item)
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
            // 根据应用状态设置状态指示器
            // 编辑中: 隐藏指示器, 使用中: 显示绿色指示器, 已存档: 隐藏指示器
            val context = binding.root.context
            val status = when {
                // 检查是否有正在运行的备份/恢复任务
                item.archives.isEmpty() -> {
                    "EDITING" // 编辑中
                }
                hasActiveOperations(item) -> {
                    "ACTIVE" // 使用中
                }
                else -> {
                    "ARCHIVED" // 已存档
                }
            }
            
            when (status) {
                "ACTIVE" -> {
                    // 使用中：显示绿色指示器
                    val colorDrawable = android.graphics.drawable.ColorDrawable(android.graphics.Color.GREEN)
                    binding.appStatusIndicator.background = colorDrawable
                    binding.appStatusIndicator.visibility = android.view.View.VISIBLE
                }
                else -> {
                    // 编辑中或已存档：隐藏指示器
                    binding.appStatusIndicator.visibility = android.view.View.GONE
                }
            }
        }
        
        private fun hasActiveOperations(item: SnapedApp): Boolean {
            // 检查是否有活跃的备份/恢复操作
            // 这里可以根据实际的存档状态来判断
            // 例如：检查是否有正在运行的任务，或最近的任务状态
            // 暂时返回false，实际实现需要根据存档状态判断
            // 在实际应用中，这可能需要检查存档的最新操作状态
           return false
        }

        // 添加一个方法来处理拖拽事件
        fun setOnStartDragListener(startDragListener: (() -> Unit)?) {
            if (startDragListener != null) {
                binding.root.setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        startDragListener.invoke()
                    }
                    false
                }
            } else {
                binding.root.setOnTouchListener(null)
            }
        }

        private fun showPopupMenu(item: SnapedApp) {
            val popupBinding =
                LayoutPopupMenuBinding.inflate(LayoutInflater.from(binding.root.context))
            val popupWindow = PopupWindow(
                popupBinding.root,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            // 设置背景和点击外部消失
            popupWindow.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            popupWindow.isOutsideTouchable = true
            popupWindow.isFocusable = true

            // 上半部分按钮功能
            popupBinding.btnEdit.setOnClickListener {
                // 编辑存档名称 - 显示对话框让用户输入新名称
                showEditNameDialog(item)
                popupWindow.dismiss()
            }

            popupBinding.btnSettings.setOnClickListener {
                // 显示AppConfigFragment作为BottomSheet
                val fragment = AppConfigFragment.newInstance(item.appInfo.packageName)
                fragment.show(groupsHolder.fragmentManager, fragment.tag)
                popupWindow.dismiss()
            }

            // 检查应用是否已安装
            val isAppInstalled = item.appInfo.appManager.isInstalled(item.appInfo.packageName, item.appInfo.userId)
            
            // 根据应用安装状态控制信息按钮的可见性
            popupBinding.btnInfo.visibility = if (isAppInstalled) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
            
            // 为信息按钮设置点击事件，跳转到应用详情页面
            popupBinding.btnInfo.setOnClickListener {
                if (isAppInstalled) {
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = Uri.fromParts("package", item.appInfo.packageName, null)
                        intent.data = uri
                        binding.root.context.startActivity(intent)
                    } catch (e: Exception) {
                        // 如果无法打开应用详情页面，尝试其他方法
                        try {
                            val fallbackIntent = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                            binding.root.context.startActivity(fallbackIntent)
                        } catch (ex: Exception) {
                            Toast.makeText(binding.root.context, "无法打开应用详情", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                popupWindow.dismiss()
            }

            popupBinding.btnDelete.setOnClickListener {
                // 删除存档 - 显示确认对话框
                showDeleteConfirmationDialog(item) {
                    popupWindow.dismiss()
                }
            }

            // 下半部分存档列表
            val archiveAdapter = ArchiveItemAdapter { archiveItem ->
                // 点击存档列表项时调用viewModel.onGroupItemClicked
                viewModel.onGroupItemClicked(group.id, group.mmkv, item.appInfo.packageName, item)
                popupWindow.dismiss()
            }

            popupBinding.archiveList.layoutManager = LinearLayoutManager(binding.root.context)
            popupBinding.archiveList.adapter = archiveAdapter

            // 设置存档列表数据
            val archives = item.archives.values.toList()
            archiveAdapter.submitList(archives)

            // 显示弹窗
            popupWindow.showAsDropDown(binding.root)
        }

        private fun showEditNameDialog(item: SnapedApp) {
            // 显示一个对话框让用户选择要重命名哪个存档
            val archiveNames = item.archives.keys.toTypedArray()
            
            if (archiveNames.isEmpty()) {
                Toast.makeText(binding.root.context, "没有可重命名的存档", Toast.LENGTH_SHORT).show()
                return
            }
            
            val builder = AlertDialog.Builder(binding.root.context)
            builder.setTitle("选择要重命名的存档")
            
            builder.setItems(archiveNames) { _, which ->
                val selectedArchiveName = archiveNames[which]
                val archivePath = item.archives[selectedArchiveName]?.path ?: return@setItems
                
                // 弹出输入框让用户输入新的存档名称
                val input = android.widget.EditText(binding.root.context)
                input.setText(selectedArchiveName)
                
                AlertDialog.Builder(binding.root.context)
                    .setTitle("重命名存档: $selectedArchiveName")
                    .setView(input)
                    .setPositiveButton("确定") { _, _ ->
                        val newName = input.text.toString().trim()
                        if (newName.isNotEmpty() && newName != selectedArchiveName) {
                            // 重命名存档
                            viewModel.viewModelScope.launch {
                                val fs = SnapShotApp.getInstance().fileSystem
                                val success = ArchiveRenameHelper.renameArchive(
                                    fs,
                                    archivePath,
                                    selectedArchiveName,
                                    newName
                                )
                                
                                withContext(Dispatchers.Main) {
                                    if (success) {
                                        Toast.makeText(binding.root.context, "存档 '$selectedArchiveName' 已重命名为 '$newName'", Toast.LENGTH_SHORT).show()
                                        // 刷新UI
                                        item.loadArchives(fs, SnapShotApp.getInstance().appManager, true) // 重新加载存档
                                        groupsHolder.refresh(group, groupsHolder.binding.groupRecyclerView)
                                        // 通知全局ViewModel更新数据，以触发RecyclerView的更新
                                        SnapShotApp.getViewModel().loadGroups()
                                    } else {
                                        Toast.makeText(binding.root.context, "重命名失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } else if (newName == selectedArchiveName) {
                            Toast.makeText(binding.root.context, "新名称与原名称相同", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            
            builder.setNegativeButton("取消", null)
            builder.show()
        }

        private fun showDeleteConfirmationDialog(item: SnapedApp, onDismiss: () -> Unit) {
            val context = binding.root.context
            AlertDialog.Builder(context)
                .setTitle("删除所有存档？")
                .setMessage("确定要删除应用所有存档吗？此操作不可恢复。")
                .setPositiveButton("删除") { _, _ ->
                    group.apps.remove(item)
                    groupsHolder.refresh(group, groupsHolder.binding.groupRecyclerView)
                    viewModel.viewModelScope.launch {
                        val fs = SnapShotApp.getInstance().fileSystem
                        val success = try {
                            fs.delete(item.packageDir)
                            fs.delete(item.iconFile)
                            true
                        } catch (e: Exception) {
                            e.printStackTrace()
                            false
                        }
                        withContext(Dispatchers.Main) {
                            if (success) {
                                Toast.makeText(context, "删除应用成功", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "删除失败: ${'$'}{e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        group.loadApps(
                            SnapShotApp.getInstance().fileSystem,
                            SnapShotApp.getInstance().appManager,
                            true
                        )
                        withContext(Dispatchers.Main) {
                            groupsHolder.refresh(group, groupsHolder.binding.groupRecyclerView)
                            // 通知全局ViewModel更新数据，以触发RecyclerView的更新
                            SnapShotApp.getViewModel().loadGroups()
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .setOnDismissListener { onDismiss() }
                .show()
        }
    }

    private class ItemDiffCallback : DiffUtil.ItemCallback<SnapedApp>() {
        override fun areItemsTheSame(oldItem: SnapedApp, newItem: SnapedApp): Boolean {
            return oldItem === newItem
        }

        override fun areContentsTheSame(oldItem: SnapedApp, newItem: SnapedApp): Boolean {
            return oldItem == newItem
        }
    }
}
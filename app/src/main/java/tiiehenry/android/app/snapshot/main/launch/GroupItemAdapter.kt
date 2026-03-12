package tiiehenry.android.app.snapshot.main.launch

import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.app.AppConfigFragment
import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.config.AppConfig
import tiiehenry.android.app.snapshot.data.ArchivedApks
import tiiehenry.android.app.snapshot.data.SnapShotMaker
import tiiehenry.android.app.snapshot.databinding.ItemAppBinding
import tiiehenry.android.app.snapshot.databinding.LayoutPopupMenuBinding
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.group.SnapedApp
import tiiehenry.android.app.snapshot.model.PackageStatus
import tiiehenry.android.app.snapshot.ui.dialog.LoadingDialog
import tiiehenry.android.snapshot.file.ICompressCallback
import tiiehenry.android.snapshot.fs.CompressState
import java.io.File
import kotlin.io.path.absolutePathString

/**
 * 将字符串中最后一个匹配的 oldValue 替换为 newValue
 */
private fun String.replaceLast(oldValue: String, newValue: String): String {
    val lastIndex = this.lastIndexOf(oldValue)
    return if (lastIndex != -1) {
        this.substring(0, lastIndex) + newValue + this.substring(lastIndex + oldValue.length)
    } else {
        this
    }
}

class GroupItemAdapter(
    private val groupsHolder: GroupsAdapter.GroupViewHolder,
    private val groupsAdapter: GroupsAdapter,
    private val viewModel: LauncherViewModel,
    private val group: SnapGroup,
    private val onItemUpdated: (GroupItemAdapter, SnapedApp) -> Unit = { a, s -> } // 添加更新回调){}){}
) : ListAdapter<SnapedApp, GroupItemAdapter.ViewHolder>(ItemDiffCallback()) {

    // 添加删除状态标志
    var isDeleteMode = false
        private set

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

            binding.root.setOnClickListener {
                // 排序模式下禁用点击
                if (groupsHolder.isSortMode) {
                    return@setOnClickListener
                }

                // 检查应用是否已安装
                val isAppInstalled = item.appInfo.appManager.isInstalled(
                    item.appInfo.packageName,
                    item.appInfo.userId
                )

                if (isAppInstalled) {
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
            val context = binding.root.context

            // 判断应用状态
            val status = getPackageStatus(item)

            // 根据状态设置图标和可见性
            when (status) {
                PackageStatus.NOT_INSTALLED -> {
                    binding.appStatusIndicator.visibility = android.view.View.VISIBLE
                    binding.appStatusIndicator.setImageResource(R.drawable.ic_status_not_installed)
                }

                PackageStatus.INSTALLED -> {
                    binding.appStatusIndicator.visibility = android.view.View.VISIBLE
                    binding.appStatusIndicator.setImageResource(R.drawable.ic_status_installed)
                }

                PackageStatus.CAN_UPDATE -> {
                    binding.appStatusIndicator.visibility = android.view.View.VISIBLE
                    binding.appStatusIndicator.setImageResource(R.drawable.ic_status_can_update)
                }
            }
        }

        /**
         * 更新运行状态指示器
         */
        private fun updateRunningIndicator(item: SnapedApp) {
            val isRunning = item.isRunning
            binding.appRunIndicator.visibility = if (isRunning) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }

        /**
         * 获取应用包状态
         */
        private fun getPackageStatus(item: SnapedApp): PackageStatus {
            val appManager = item.appInfo.appManager
            val packageName = item.appInfo.packageName
            val userId = item.appInfo.userId

            // 检查应用是否安装
            val isInstalled = try {
                appManager.isInstalled(packageName, userId)
            } catch (e: Exception) {
                false
            }

            if (!isInstalled) {
                return PackageStatus.NOT_INSTALLED
            }

            // 获取存档中最新版本的versionCode
            val latestArchiveVersion = item.latestArchive?.metaInfo?.packageInfo?.versionCode

            // 如果没有存档，返回已安装状态
            if (latestArchiveVersion == null) {
                return PackageStatus.INSTALLED
            }

            // 获取已安装应用的versionCode
            val installedVersion = try {
                val packageInfo = appManager.getPackageInfo(packageName, 0, userId)
                packageInfo?.longVersionCode ?: 0L
            } catch (e: Exception) {
                0L
            }

            // 比较版本：存档版本高于已安装版本表示可更新
            return if (latestArchiveVersion != installedVersion) {
                PackageStatus.CAN_UPDATE
            } else {
                PackageStatus.INSTALLED
            }
        }

        private fun hasActiveOperations(item: SnapedApp): Boolean {
            // 使用 IAppManager 检查应用是否正在运行
            return try {
                item.appInfo.appManager.isPackageRunning(
                    item.appInfo.packageName,
                    item.appInfo.userId
                )
            } catch (e: Exception) {
                false
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
            Log.d("GroupItemAdapter", "showPopupMenu called for app ${item.appInfo.packageName}")
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
            // 设置阴影，模拟 PopupMenu 的阴影效果
            popupWindow.elevation = 16f * binding.root.resources.displayMetrics.density

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
            val isAppInstalled =
                item.appInfo.appManager.isInstalled(item.appInfo.packageName, item.appInfo.userId)

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
                            val fallbackIntent =
                                Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                            binding.root.context.startActivity(fallbackIntent)
                        } catch (ex: Exception) {
                            Toast.makeText(
                                binding.root.context,
                                "无法打开应用详情",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                popupWindow.dismiss()
            }

            // 为存档按钮设置点击事件，实现应用存档
            popupBinding.btnShot.setOnClickListener {
                // 实现应用存档，弹出Loading对话框
                createSnapshot(item)
                popupWindow.dismiss()
            }

            popupBinding.btnDelete.setOnLongClickListener {
                // 删除存档 - 显示确认对话框
                showDeleteConfirmationDialog(item) {
                    popupWindow.dismiss()
                }
                true
            }
            // 先声明adapter变量
            lateinit var archiveAdapter: ArchiveItemAdapter

            // 创建adapter实例
            archiveAdapter = ArchiveItemAdapter(
                onItemClick = { archiveItem: ArchiveItem, needConfirm: Boolean ->
                    //这是正常状态的点击事件
                    // 点击存档列表项时调用viewModel.onGroupItemClicked
                    if (needConfirm) {
                        // 点击图标时需要二次确认
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
                                popupWindow.dismiss()
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    } else {
                        viewModel.onGroupItemClicked(
                            binding.root.context,
                            group.id,
                            group.mmkv,
                            item.appInfo.packageName,
                            item
                        )
                        popupWindow.dismiss()
                    }
                },
                onDeleteClick = { archiveItem ->
                    // 删除存档
                    deleteArchiveAsync(item, archiveItem) { success ->
                        if (success) {
                            // 删除成功后从列表中移除该项
                            val currentList = archiveAdapter.currentList.toMutableList()
                            currentList.removeAll { it.name == archiveItem.name }
                            archiveAdapter.submitList(currentList) {
                                archiveAdapter.notifyDataSetChanged()
                            }
                        }
                    }
                },
                onRenameSuccess = { oldName, newName ->
                    item.loadArchives(item.appInfo.fs, SnapshotApp.getInstance().appManager, true)
                    archiveAdapter.submitList(item.archives.values.toList().sortedByDescending { it.metaInfo.makeTime })
                },
                onAdvancedRestoreClick = { archiveItem, selectedTypes ->
                    // 高级恢复：只恢复选中的数据类型
                    viewModel.onAdvancedRestoreClicked(
                        binding.root.context,
                        item,
                        archiveItem,
                        selectedTypes
                    )
                    popupWindow.dismiss()
                }
            )

            popupBinding.archiveList.layoutManager = LinearLayoutManager(binding.root.context)
            popupBinding.archiveList.adapter = archiveAdapter

            // 只在存档列表为空时重新加载，避免每次弹出popupWindow时重复加载
            if (item.archives.isEmpty()) {
                item.loadArchives(item.appInfo.fs, SnapshotApp.getInstance().appManager, true)
            }
            // 设置存档列表数据
            val archives = item.archives.values.toList().sortedByDescending { it.metaInfo.makeTime }
            Log.i("GroupItemAdapter", "Archives: ${archives.size}")
            archiveAdapter.submitList(archives)

            popupBinding.btnDelete.setOnClickListener {
                // 切换删除状态
                adapter.isDeleteMode = !adapter.isDeleteMode

                // 更新删除按钮的外观
                if (adapter.isDeleteMode) {
                    // 进入删除状态 - 改为完成图标
                    popupBinding.btnDelete.setImageResource(R.drawable.check)
                } else {
                    // 退出删除状态 - 恢复删除图标
                    popupBinding.btnDelete.setImageResource(R.drawable.delete_forever_outline)
                }

                // 通知archiveAdapter更新删除状态
                archiveAdapter.setDeleteMode(adapter.isDeleteMode)

                // 如果退出删除模式，刷新列表以恢复正常显示
                if (!adapter.isDeleteMode) {
                    archiveAdapter.notifyDataSetChanged()
                }

//                popupWindow.dismiss()
            }

            // 显示弹窗
            popupWindow.showAsDropDown(binding.root)
        }

        private fun showEditNameDialog(item: SnapedApp) {
            // 提示用户：重命名功能已移动到存档列表中
            // 现在需要长按存档项，在弹出的信息对话框中点击"重命名"按钮
            Toast.makeText(
                binding.root.context,
                "请长按下方存档列表中的存档项，在信息对话框中点击\"重命名\"",
                Toast.LENGTH_LONG
            ).show()
        }

        private fun showDeleteConfirmationDialog(item: SnapedApp, onDismiss: () -> Unit) {
            Log.d(
                "GroupItemAdapter",
                "showDeleteConfirmationDialog called for app ${item.appInfo.packageName}"
            )
            val context = binding.root.context
            AlertDialog.Builder(context)
                .setTitle("删除所有存档？")
                .setMessage("确定要删除应用所有存档吗？此操作不可恢复。")
                .setPositiveButton("清空存档") { _, _ ->
                    viewModel.viewModelScope.launch {
                        val fs = SnapshotApp.getInstance().fileSystem
                        val success = try {
                            // 删除存档子目录，但保留图标文件
                            val archiveNames = fs.listDir(item.packageDir)
                            for (archiveName in archiveNames) {
                                val archivePath =
                                    java.nio.file.Paths.get(item.packageDir, archiveName)
                                        .absolutePathString()
                                // 跳过图标文件，只删除存档目录
                                if (archivePath != item.iconFile) {
                                    fs.delete(archivePath)
                                }
                            }
                            true
                        } catch (e: Exception) {
                            e.printStackTrace()
                            false
                        }
                        withContext(Dispatchers.Main) {
                            if (success) {
                                Toast.makeText(context, "删除存档成功", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "删除失败",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            item.loadArchives(
                                item.appInfo.fs,
                                SnapshotApp.getInstance().appManager,
                                true
                            )
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .setNeutralButton("删除全部") { _, _ ->
                    Log.i(
                        "GroupItemAdapter",
                        "Deleting all archives for ${item.appInfo.packageName}"
                    )
                    viewModel.viewModelScope.launch {
                        val fs = SnapshotApp.getInstance().fileSystem
                        val success = try {
                            // 完全删除应用目录和图标文件
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
                                    "删除失败",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            // 从组中移除应用并刷新 UI
                            group.apps.remove(item)
                            groupsHolder.refresh(group, groupsHolder.binding.groupRecyclerView)
                            // 通知全局 ViewModel 更新数据，以触发 RecyclerView 的更新
                            SnapshotApp.getViewModel().loadGroups()
                        }
                    }
                }
                .setOnDismissListener { onDismiss() }
                .show()
        }

        /**
         * 显示单个存档删除确认对话框
         */
        private fun deleteArchiveAsync(
            item: SnapedApp,
            archiveItem: ArchiveItem,
            onResult: (Boolean) -> Unit
        ) {
            val context = binding.root.context
            viewModel.viewModelScope.launch {
                val fs = SnapshotApp.getInstance().fileSystem
                val success = try {
                    // 删除指定的存档目录
                    fs.delete(archiveItem.path)
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
                val archivedApkDirPath = ArchivedApks.getArchivedApkDir(
                    item.packageDir,
                    archiveItem.metaInfo.packageInfo.versionCode
                )
                val archivedApkDir = File(archivedApkDirPath)
                val archiveFiles =
                    archivedApkDir.listFiles { it.name.startsWith("${archiveItem.metaInfo.packageInfo.size}") }
                // 检查是否有其他archiveItem引用了相同的versionCode和size
                val versionCode = archiveItem.metaInfo.packageInfo.versionCode
                val size = archiveItem.metaInfo.packageInfo.size
                val isReferencedByOther = synchronized(item.archives) {
                    item.archives.values.any { other ->
                        other != archiveItem &&
                                other.metaInfo.packageInfo.versionCode == versionCode &&
                                other.metaInfo.packageInfo.size == size
                    }
                }
                // 如果没有被引用，删除archiveFiles
                if (!isReferencedByOther && archiveFiles != null) {
                    archiveFiles.forEach { file ->
                        try {
                            file.delete()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                if (archivedApkDir.list()?.isEmpty() == true) {
                    archivedApkDir.delete()
                }

                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(context, "存档删除成功", Toast.LENGTH_SHORT).show()
                        // 重新加载应用的存档列表
                        item.loadArchives(fs, SnapshotApp.getInstance().appManager, true)
                        onResult(true)
                    } else {
                        Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
                        onResult(false)
                    }
                }
            }
        }

        private fun launchApp(packageName: String, userId: Int) {
            val context = binding.root.context
            try {
                val appManager = SnapshotApp.getInstance().appManager
                val success = appManager.launchApp(packageName, userId)
                if (!success) {
                    Toast.makeText(context, "无法启动应用", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "启动应用失败: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        /**
         * 创建应用快照
         */
        private fun createSnapshot(item: SnapedApp) {
            val context = binding.root.context
            val loadingDialog = LoadingDialog(context)
            loadingDialog.setMessage("正在创建存档...")
            loadingDialog.show()

            viewModel.viewModelScope.launch(Dispatchers.Default) {
                try {
                    val snapShotApp = SnapshotApp.getInstance()
                    val fs = snapShotApp.fileSystem
                    val appManager = snapShotApp.appManager

                    // 获取应用配置
                    val appConfig = AppConfig(item.appInfo.packageName)
                    val groupConfig = group.config

                    // 挂起应用（应用进程暂停运行）
                    appManager.suspendPackage(item.appInfo.packageName, item.appInfo.userId)

                    // 创建简单的压缩回调
                    val callback = object : ICompressCallback.Stub() {
                        override fun onStart() {
//                            viewModel.viewModelScope.launch(Dispatchers.Main) {
//                                loadingDialog.setMessage("打包中...")
//                            }
                        }

                        override fun onProgress(bytesWritten: Long, kbPerS: Long) {
                            viewModel.viewModelScope.launch(Dispatchers.Main) {
                                val fileSize = Formatter.formatFileSize(context, bytesWritten)
                                val fileSize1 = Formatter.formatFileSize(context, kbPerS)
                                val message = "已写入: $fileSize\n" +
                                        "速度: $fileSize1/s"
                                loadingDialog.setMessage(message)
                            }
                        }

                        override fun onDone(originSize: Long, targetSize: Long, md5: String) {
                        }

                        override fun onError(msg: String?) {
                            viewModel.viewModelScope.launch(Dispatchers.Main) {
                                loadingDialog.dismiss()
                                Toast.makeText(context, "存档失败: $msg", Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                    val tasks = SnapShotMaker.makeSnapshot(
                        fs, appManager, item, item.appInfo, callback, groupConfig, appConfig
                    )

                    if (tasks != null) {
                        tasks.remove("meta-info")!!.let {
                            async {
                                it.start()
                            }
                        }
                        for (entry in tasks) {
                            viewModel.viewModelScope.launch(Dispatchers.Main) {
                                loadingDialog.setCurrentItem(entry.key)
                            }
                            val task = entry.value
                            task.start()
                        }

                        val hasError = tasks.values.any {
                            it.state() == CompressState.COMPRESS_STATE_ERROR
                        }

                        viewModel.viewModelScope.launch(Dispatchers.Main) {
                            loadingDialog.dismiss()

                            if (hasError) {
                                Toast.makeText(context, "存档过程中出现错误", Toast.LENGTH_LONG)
                                    .show()
                            } else {
                                Toast.makeText(context, "存档创建成功", Toast.LENGTH_SHORT)
                                    .show()
                                // 重新加载应用数据
                                item.loadArchives(fs, appManager, true)
                                groupsHolder.refresh(
                                    group,
                                    groupsHolder.binding.groupRecyclerView
                                )
                                SnapshotApp.getViewModel().loadGroups()
                            }
                        }
                    } else {
                        viewModel.viewModelScope.launch(Dispatchers.Main) {
                            loadingDialog.dismiss()
                            Toast.makeText(context, "存档创建失败", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    viewModel.viewModelScope.launch(Dispatchers.Main) {
                        loadingDialog.dismiss()
                        Toast.makeText(context, "存档失败: ${e.message}", Toast.LENGTH_LONG)
                            .show()
                    }
                } finally {
                    // 用IAppManager实现恢复挂起应用
                    try {
                        val snapShotApp = SnapshotApp.getInstance()
                        val appManager = snapShotApp.appManager
                        appManager.unsuspendPackage(item.appInfo.packageName, item.appInfo.userId)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Log.e("GroupItemAdapter", "Failed to unsuspend package", e)
                    }
                }
            }
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
package tiiehenry.android.app.snapshot.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.PopupWindow
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.app.AppConfigFragment
import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.data.ArchiveManager
import tiiehenry.android.app.snapshot.databinding.LayoutPopupMenuBinding
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.group.SnapedApp
import tiiehenry.android.app.snapshot.main.launch.ArchiveItemAdapter
import tiiehenry.android.app.snapshot.util.AppStatusHelper

/**
 * 弹出菜单帮助类
 * 负责管理应用项的长按弹出菜单
 */
class ArchiveItemPopupMenu(
    private val context: Context,
    private val fragmentManager: FragmentManager,
    private val coroutineScope: CoroutineScope
) {

    /**
     * 弹出菜单回调接口
     */
    interface Callback {
        fun onArchiveItemClick(
            item: SnapedApp,
            archiveItem: ArchiveItem,
            needConfirm: Boolean,
            archiveAdapter: ArchiveItemAdapter
        )
        fun onAdvancedRestoreClick(
            item: SnapedApp,
            archiveItem: ArchiveItem,
            selectedTypes: Set<String>
        )

        fun onCreateSnapshot(item: SnapedApp)
        fun onClearAllArchives(item: SnapedApp, onComplete: () -> Unit)
        fun onDeleteApp(item: SnapedApp, onComplete: () -> Unit)
        fun onLockStateChanged(item: SnapedApp, isLocked: Boolean)
        fun deleteArchive(
            item: SnapedApp,
            archiveItem: ArchiveItem,
            archiveAdapter: ArchiveItemAdapter
        )
    }

    /**
     * 显示弹出菜单
     * @param anchor 锚点视图
     * @param item 应用快照项
     * @param group 所属组
     * @param callback 菜单操作回调
     */
    fun showPopupMenu(
        anchor: View,
        item: SnapedApp,
        group: SnapGroup,
        callback: Callback
    ) {
        val popupBinding = LayoutPopupMenuBinding.inflate(LayoutInflater.from(context))
        val popupWindow = PopupWindow(
            popupBinding.root,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // 设置背景和点击外部消失
        popupWindow.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        popupWindow.isOutsideTouchable = true
        popupWindow.isFocusable = true
        popupWindow.elevation = 16f * context.resources.displayMetrics.density

        // 设置存档列表
        val archiveItemAdapter = setupArchiveList(popupBinding, item, callback, popupWindow)

        // 设置按钮点击事件
        setupButtonListeners(
            popupBinding,
            item,
            group,
            popupWindow,
            archiveItemAdapter,
            callback
        )

        // 显示弹窗
        popupWindow.showAsDropDown(anchor)
    }

    /**
     * 设置按钮监听器
     */
    private fun setupButtonListeners(
        popupBinding: LayoutPopupMenuBinding,
        item: SnapedApp,
        group: SnapGroup,
        popupWindow: PopupWindow,
        archiveItemAdapter: ArchiveItemAdapter,
        callback: Callback
    ) {
        // 锁定按钮 - 如果锁定在 GroupConfig.lockedList 中添加包名，否则移除
        val packageName = item.appInfo.packageName
        val isLocked = group.config.isLocked(packageName)
        fun updateLockIcon(isLocked: Boolean) {
            popupBinding.btnLock.setImageResource(
                if (isLocked) R.drawable.lock_open_minus_outline else R.drawable.lock_plus_outline
            )
        }
        updateLockIcon(isLocked)
        popupBinding.btnLock.setOnClickListener {
            val newLockState = !isLocked
            if (isLocked) {
                group.config.removeFromLockedList(packageName)
                Toast.makeText(context, "已解锁应用，自动卸载跟随应用或组策略", Toast.LENGTH_SHORT)
                    .show()
            } else {
                group.config.addToLockedList(packageName)
                Toast.makeText(context, "已锁定应用，应用不会被自动卸载", Toast.LENGTH_SHORT).show()
            }
            updateLockIcon(newLockState)
            callback.onLockStateChanged(item, newLockState)
            popupWindow.dismiss()
        }

        // 设置按钮
        popupBinding.btnSettings.setOnClickListener {
            val fragment = AppConfigFragment.newInstance(item.appInfo.packageName)
            fragment.show(fragmentManager, fragment.tag)
            popupWindow.dismiss()
        }

        // 根据应用安装状态控制信息按钮的可见性
        val isAppInstalled = AppStatusHelper.isAppInstalled(item)
        popupBinding.btnInfo.isEnabled = isAppInstalled
        popupBinding.btnShot.isEnabled = isAppInstalled

        // 信息按钮
        popupBinding.btnInfo.setOnClickListener {
            if (isAppInstalled) {
                openAppSettings(item.appInfo.packageName)
            }
            popupWindow.dismiss()
        }

        // 存档按钮
        popupBinding.btnShot.setOnClickListener {
            callback.onCreateSnapshot(item)
            popupWindow.dismiss()
        }

        // 删除按钮长按 - 显示删除确认对话框
        popupBinding.btnDelete.setOnLongClickListener {
            showDeleteConfirmationDialog(item, callback) {
                popupWindow.dismiss()
            }
            true
        }

        // 删除按钮点击 - 切换删除模式
        popupBinding.btnDelete.setOnClickListener {
            val newDeleteMode = archiveItemAdapter.toggleDeleteMode()

            // 更新删除按钮的外观
            if (newDeleteMode) {
                popupBinding.btnDelete.setImageResource(R.drawable.check)
            } else {
                popupBinding.btnDelete.setImageResource(R.drawable.delete_forever_outline)
            }
        }
    }

    /**
     * 设置存档列表
     */
    private fun setupArchiveList(
        popupBinding: LayoutPopupMenuBinding,
        item: SnapedApp,
        callback: Callback,
        popupWindow: PopupWindow
    ): ArchiveItemAdapter {
        lateinit var archiveAdapter: ArchiveItemAdapter

        archiveAdapter = ArchiveItemAdapter(
            onItemClick = { archiveItem, needConfirm ->
                callback.onArchiveItemClick(item, archiveItem, needConfirm,archiveAdapter)
                popupWindow.dismiss()
            },
            onDeleteClick = { archiveItem ->
                callback.deleteArchive(item, archiveItem, archiveAdapter)
            },
            onRenameSuccess = { _, _ ->
                coroutineScope.launch {
                    ArchiveManager.reloadArchives(item, true)
                    archiveAdapter.submitList(ArchiveManager.getSortedArchives(item))
                }
            },
            onAdvancedRestoreClick = { archiveItem, selectedTypes ->
                callback.onAdvancedRestoreClick(item, archiveItem, selectedTypes)
                popupWindow.dismiss()
            }
        )

        popupBinding.archiveList.layoutManager = LinearLayoutManager(context)
        popupBinding.archiveList.adapter = archiveAdapter

        // 只在存档列表为空时重新加载
        if (item.archives.isEmpty()) {
            coroutineScope.launch {
                ArchiveManager.reloadArchives(item, true)
            }
        }

        // 设置存档列表数据
        archiveAdapter.submitList(ArchiveManager.getSortedArchives(item))
        return archiveAdapter
    }

    /**
     * 显示编辑名称提示
     */
    private fun showEditNameHint() {
        Toast.makeText(
            context,
            "请长按下方存档列表中的存档项，在信息对话框中点击\"重命名\"",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * 打开应用设置页面
     */
    private fun openAppSettings(packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                context.startActivity(fallbackIntent)
            } catch (ex: Exception) {
                Toast.makeText(context, "无法打开应用详情", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 显示删除确认对话框
     */
    private fun showDeleteConfirmationDialog(
        item: SnapedApp,
        callback: Callback,
        onDismiss: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle("删除所有存档？")
            .setMessage("确定要删除应用所有存档吗？此操作不可恢复。")
            .setPositiveButton("清空存档") { _, _ ->
                callback.onClearAllArchives(item, onDismiss)
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("删除全部") { _, _ ->
                callback.onDeleteApp(item, onDismiss)
            }
            .show()
    }
}

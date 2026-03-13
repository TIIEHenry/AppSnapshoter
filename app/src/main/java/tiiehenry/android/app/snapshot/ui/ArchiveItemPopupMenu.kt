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
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.app.AppConfigFragment
import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.data.ArchiveManager
import tiiehenry.android.app.snapshot.databinding.LayoutPopupMenuBinding
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.group.SnapedApp
import tiiehenry.android.app.snapshot.main.launch.ArchiveItemAdapter
import tiiehenry.android.app.snapshot.util.AppStatusHelper
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
        fun onArchiveItemClick(item: SnapedApp, archiveItem: ArchiveItem, needConfirm: Boolean)
        fun onAdvancedRestoreClick(item: SnapedApp, archiveItem: ArchiveItem, selectedTypes: Set<String>)
        fun onCreateSnapshot(item: SnapedApp)
        fun onClearAllArchives(item: SnapedApp, onComplete: () -> Unit)
        fun onDeleteApp(item: SnapedApp, onComplete: () -> Unit)
    }

    /**
     * 显示弹出菜单
     * @param anchor 锚点视图
     * @param item 应用快照项
     * @param group 所属组
     * @param isDeleteMode 是否处于删除模式
     * @param onDeleteModeChanged 删除模式变更回调
     * @param callback 菜单操作回调
     */
    fun showPopupMenu(
        anchor: View,
        item: SnapedApp,
        group: SnapGroup,
        isDeleteMode: Boolean,
        onDeleteModeChanged: (Boolean) -> Unit,
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

        // 设置按钮点击事件
        setupButtonListeners(popupBinding, item, group, popupWindow, isDeleteMode, onDeleteModeChanged, callback)

        // 设置存档列表
        setupArchiveList(popupBinding, item, callback, popupWindow)

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
        isDeleteMode: Boolean,
        onDeleteModeChanged: (Boolean) -> Unit,
        callback: Callback
    ) {
        // 编辑按钮
        popupBinding.btnEdit.setOnClickListener {
            showEditNameHint()
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
        popupBinding.btnInfo.visibility = if (isAppInstalled) View.VISIBLE else View.GONE

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
            val newDeleteMode = !isDeleteMode
            onDeleteModeChanged(newDeleteMode)

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
    ) {
        lateinit var archiveAdapter: ArchiveItemAdapter

        archiveAdapter = ArchiveItemAdapter(
            onItemClick = { archiveItem, needConfirm ->
                callback.onArchiveItemClick(item, archiveItem, needConfirm)
                popupWindow.dismiss()
            },
            onDeleteClick = { archiveItem ->
                deleteArchive(item, archiveItem, archiveAdapter)
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
    }

    /**
     * 删除存档
     */
    private fun deleteArchive(
        item: SnapedApp,
        archiveItem: ArchiveItem,
        archiveAdapter: ArchiveItemAdapter
    ) {
        coroutineScope.launch {
            val success = ArchiveManager.deleteArchive(item, archiveItem)
            if (success) {
                // 删除成功后从列表中移除该项
                val currentList = archiveAdapter.currentList.toMutableList()
                currentList.removeAll { it.name == archiveItem.name }
                archiveAdapter.submitList(currentList) {
                    archiveAdapter.notifyDataSetChanged()
                }
                Toast.makeText(context, "存档删除成功", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
            }
        }
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
            .setOnDismissListener { onDismiss() }
            .show()
    }
}

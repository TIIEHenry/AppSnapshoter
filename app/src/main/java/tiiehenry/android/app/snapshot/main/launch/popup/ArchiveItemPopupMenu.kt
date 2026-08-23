package tiiehenry.android.app.snapshot.main.launch.popup

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.main.launch.app.AppConfigFragment
import tiiehenry.android.app.snapshot.archive.manage.ArchiveManager
import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.databinding.LayoutPopupMenuBinding
import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.utils.AppDetailsLauncher
import tiiehenry.android.app.snapshot.utils.AppStatusHelper

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
            item: ArchivedApp,
            archiveItem: ArchiveItem,
            needConfirm: Boolean,
            archiveAdapter: ArchiveItemAdapter
        )
        fun onAdvancedRestoreClick(
            item: ArchivedApp,
            archiveItem: ArchiveItem,
            selectedTypes: Set<String>
        )

        fun onCreateSnapshot(item: ArchivedApp)
        fun onClearAllArchives(item: ArchivedApp, onComplete: () -> Unit)
        fun onDeleteApp(item: ArchivedApp, onComplete: () -> Unit)
        fun onLockStateChanged(item: ArchivedApp, isLocked: Boolean)
        fun deleteArchive(
            item: ArchivedApp,
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
        item: ArchivedApp,
        group: SnapGroup,
        callback: Callback
    ) {
        val popupBinding = LayoutPopupMenuBinding.inflate(LayoutInflater.from(context))
        val popupWindow = PopupWindow(
            popupBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // 设置背景和点击外部消失
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
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
        item: ArchivedApp,
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
                Toast.makeText(context, R.string.app_unlocked_auto_uninstall, Toast.LENGTH_SHORT)
                    .show()
            } else {
                group.config.addToLockedList(packageName)
                Toast.makeText(context, R.string.app_locked_no_auto_uninstall, Toast.LENGTH_SHORT).show()
            }
            updateLockIcon(newLockState)
            callback.onLockStateChanged(item, newLockState)
            popupWindow.dismiss()
        }

        // 设置按钮
        popupBinding.btnSettings.setOnClickListener {
            val fragment = AppConfigFragment.Companion.newInstance(
                item.appInfo.packageName,
                item.appInfo.userId
            )
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
                AppDetailsLauncher.open(context, item.appInfo.packageName)
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
        item: ArchivedApp,
        callback: Callback,
        popupWindow: PopupWindow
    ): ArchiveItemAdapter {
        lateinit var archiveAdapter: ArchiveItemAdapter

        archiveAdapter = ArchiveItemAdapter(
            onItemClick = { archiveItem, needConfirm ->
                callback.onArchiveItemClick(item, archiveItem, needConfirm, archiveAdapter)
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
            R.string.archive_rename_hint,
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * 显示删除确认对话框
     */
    private fun showDeleteConfirmationDialog(
        item: ArchivedApp,
        callback: Callback,
        onDismiss: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(R.string.archive_delete_all_title)
            .setMessage(R.string.archive_delete_all_message)
            .setPositiveButton(R.string.archive_clear_archives) { _, _ ->
                callback.onClearAllArchives(item, onDismiss)
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.archive_delete_all) { _, _ ->
                callback.onDeleteApp(item, onDismiss)
            }
            .show()
    }
}
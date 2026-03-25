package tiiehenry.android.app.snapshot.data

import android.content.Context
import android.text.format.Formatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.config.AppConfigManager
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.group.SnapedApp
import tiiehenry.android.app.snapshot.main.launch.ArchiveFailedException
import tiiehenry.android.app.snapshot.ui.dialog.ILoadingDialog
import tiiehenry.android.app.snapshot.ui.dialog.LoadingDialog
import tiiehenry.android.app.snapshot.util.AppStatusHelper
import tiiehenry.android.snapshot.file.ICompressCallback
import tiiehenry.android.snapshot.fs.CompressState
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 快照创建管理类
 * 负责应用快照的创建流程
 */
class SnapshotCreator(
    private val context: Context,
    private val viewModelScope: kotlinx.coroutines.CoroutineScope
) {

    companion object {
        private const val TAG = "SnapshotCreator"
    }

    /**
     * 创建快照的回调接口
     */
    interface Callback {
        fun onSuccess()
        fun onError(e: Exception)
        fun onFinish()
    }

    /**
     * 创建应用快照
     * @param item 应用快照项
     * @param group 所属组
     * @param callback 回调
     */
    fun createSnapshot(item: SnapedApp, group: SnapGroup, callback: Callback? = null) {
        val loadingDialog = LoadingDialog(context)
        loadingDialog.setItemMessage("正在创建存档")
        loadingDialog.setItemStatus("...")
        loadingDialog.showItem()
        createSnapshot(loadingDialog, item, group, AtomicBoolean(false), callback)
    }

    /**
     * 创建应用快照
     * @param item 应用快照项
     * @param group 所属组
     * @param callback 回调
     */
    fun createSnapshot(
        loadingDialog: ILoadingDialog,
        item: SnapedApp,
        group: SnapGroup,
        isCanceled: AtomicBoolean,
        callback: Callback? = null
    ) {
        val onError = { msg: Exception ->
            loadingDialog.setItemException(msg)
        }
        val onErrorCallback = { msg: String ->
            onError(ArchiveFailedException(msg))
        }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val snapShotApp = SnapshotApp.getInstance()
                val fs = snapShotApp.fileSystem
                val appManager = snapShotApp.appManager

                // 获取应用配置（使用 AppConfigManager 复用实例）
                val appConfig = AppConfigManager.getInstance().getConfig(item.appInfo.packageName)
                val groupConfig = group.config

                // 挂起应用（应用进程暂停运行）
                AppStatusHelper.suspendPackage(item.appInfo.packageName, item.appInfo.userId)

                // 创建压缩回调
                val compressCallback =
                    createCompressCallback(context, loadingDialog, onErrorCallback)

                val snapshotTasks = SnapShotMaker.makeSnapshot(
                    fs, appManager, item, item.appInfo, compressCallback, groupConfig, appConfig
                )
                var currentIndex = 0
                if (snapshotTasks != null) {
                    val tasks = snapshotTasks.tasks
                    val totalTask = tasks.size
                    fun updateIndex(index: Int) {
                        loadingDialog.setItemProgress(index * 100 / totalTask)
                    }

                    // 先启动meta-info任务
                    tasks.remove("meta-info")?.let {
                        currentIndex++
                        withContext(Dispatchers.Main) {
                            updateIndex(currentIndex)
                        }
                        async { it.start() }
                    }
                    // 执行其他任务
                    for (entry in tasks) {
                        if (isCanceled.get()) {
                            continue
                        }
                        currentIndex++
                        withContext(Dispatchers.Main) {
                            updateIndex(currentIndex)
                            loadingDialog.setCurrentItem(entry.key)
                            loadingDialog.setItemMessage("处理中")
                            loadingDialog.setItemStatus("...")
                        }
                        entry.value.start()
                        if (entry.value.state() == CompressState.COMPRESS_STATE_ERROR) {
                            fs.delete(snapshotTasks.dir)
                            return@launch
                        }
                    }
                    if (isCanceled.get()) {
                        fs.delete(snapshotTasks.dir)
                        return@launch
                    }
                    // 重新加载应用数据
                    ArchiveManager.reloadArchives(item, true)
                    // 异步执行保留策略清理（不阻塞UI）
                    RetentionPolicyExecutor.applyPolicy(item, groupConfig, appConfig)
                    withContext(Dispatchers.Main) {
                        callback?.onSuccess()
                        loadingDialog.dismissItem()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onErrorCallback("no task")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    callback?.onError(e)
                    onError(e)
                }
            } finally {
                // 恢复挂起应用
                AppStatusHelper.unsuspendPackage(item.appInfo.packageName, item.appInfo.userId)
                ArchiveManager.reloadArchives(item, true)
                withContext(Dispatchers.Main) {
                    callback?.onFinish()
                }
            }
        }
    }

    /**
     * 创建压缩回调
     */
    private fun createCompressCallback(
        context: Context,
        loadingDialog: ILoadingDialog,
        onErrorCallback: (String) -> Unit
    ): ICompressCallback {
        return object : ICompressCallback.Stub() {
            override fun onStart() {
                // 可选：显示开始状态
            }

            override fun onProgress(bytesWritten: Long, bytesPerS: Long) {
                viewModelScope.launch(Dispatchers.Main) {
                    val fileSize = Formatter.formatFileSize(context, bytesWritten)
                    loadingDialog.setItemMessage("已写入: $fileSize")
                    if (bytesPerS == 0L) {
                        loadingDialog.setItemStatus("...")
                    } else {
                        val speed = Formatter.formatFileSize(context, bytesPerS)
                        loadingDialog.setItemStatus("$speed/s")
                    }
                }
            }

            override fun onDone(originSize: Long, targetSize: Long, md5: String) {
                // 任务完成时的回调
            }

            override fun onError(msg: String?) {
                viewModelScope.launch(Dispatchers.Main) {
                    onErrorCallback(msg ?: "unknow")
                }
            }
        }
    }
}

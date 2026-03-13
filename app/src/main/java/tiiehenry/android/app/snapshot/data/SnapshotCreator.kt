package tiiehenry.android.app.snapshot.data

import android.content.Context
import android.text.format.Formatter
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.config.AppConfig
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.group.SnapedApp
import tiiehenry.android.app.snapshot.ui.dialog.LoadingDialog
import tiiehenry.android.app.snapshot.util.AppStatusHelper
import tiiehenry.android.snapshot.file.ICompressCallback
import tiiehenry.android.snapshot.fs.CompressState

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
        fun onError(message: String)
    }

    /**
     * 创建应用快照
     * @param item 应用快照项
     * @param group 所属组
     * @param callback 回调
     */
    fun createSnapshot(item: SnapedApp, group: SnapGroup, callback: Callback? = null) {
        val loadingDialog = LoadingDialog(context)
        loadingDialog.setMessage("正在创建存档...")
        loadingDialog.show()

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val snapShotApp = SnapshotApp.getInstance()
                val fs = snapShotApp.fileSystem
                val appManager = snapShotApp.appManager

                // 获取应用配置
                val appConfig = AppConfig(item.appInfo.packageName)
                val groupConfig = group.config

                // 挂起应用（应用进程暂停运行）
                AppStatusHelper.suspendPackage(item.appInfo.packageName, item.appInfo.userId)

                // 创建压缩回调
                val compressCallback = createCompressCallback(context, loadingDialog, callback)

                val tasks = SnapShotMaker.makeSnapshot(
                    fs, appManager, item, item.appInfo, compressCallback, groupConfig, appConfig
                )

                if (tasks != null) {
                    // 先启动meta-info任务
                    tasks.remove("meta-info")?.let {
                        async { it.start() }
                    }

                    // 执行其他任务
                    for (entry in tasks) {
                        withContext(Dispatchers.Main) {
                            loadingDialog.setCurrentItem(entry.key)
                        }
                        entry.value.start()
                    }

                    val hasError = tasks.values.any {
                        it.state() == CompressState.COMPRESS_STATE_ERROR
                    }

                    withContext(Dispatchers.Main) {
                        loadingDialog.dismiss()

                        if (hasError) {
                            Toast.makeText(context, "存档过程中出现错误", Toast.LENGTH_LONG).show()
                            callback?.onError("存档过程中出现错误")
                        } else {
                            Toast.makeText(context, "存档创建成功", Toast.LENGTH_SHORT).show()
                            // 重新加载应用数据
                            item.loadArchives(fs, appManager, true)
                            callback?.onSuccess()

                            // 异步执行保留策略清理（不阻塞UI）
                            launch {
                                RetentionPolicyExecutor.applyPolicy(item, groupConfig, appConfig)
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        loadingDialog.dismiss()
                        Toast.makeText(context, "存档创建失败", Toast.LENGTH_LONG).show()
                        callback?.onError("存档创建失败")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    Toast.makeText(context, "存档失败: ${e.message}", Toast.LENGTH_LONG).show()
                    callback?.onError("存档失败: ${e.message}")
                }
            } finally {
                // 恢复挂起应用
                AppStatusHelper.unsuspendPackage(item.appInfo.packageName, item.appInfo.userId)
            }
        }
    }

    /**
     * 创建压缩回调
     */
    private fun createCompressCallback(
        context: Context,
        loadingDialog: LoadingDialog,
        callback: Callback?
    ): ICompressCallback {
        return object : ICompressCallback.Stub() {
            override fun onStart() {
                // 可选：显示开始状态
            }

            override fun onProgress(bytesWritten: Long, kbPerS: Long) {
                viewModelScope.launch(Dispatchers.Main) {
                    val fileSize = Formatter.formatFileSize(context, bytesWritten)
                    val speed = Formatter.formatFileSize(context, kbPerS)
                    val message = "已写入: $fileSize\n速度: $speed/s"
                    loadingDialog.setMessage(message)
                }
            }

            override fun onDone(originSize: Long, targetSize: Long, md5: String) {
                // 任务完成时的回调
            }

            override fun onError(msg: String?) {
                viewModelScope.launch(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    Toast.makeText(context, "存档失败: $msg", Toast.LENGTH_LONG).show()
                    callback?.onError("存档失败: $msg")
                }
            }
        }
    }
}

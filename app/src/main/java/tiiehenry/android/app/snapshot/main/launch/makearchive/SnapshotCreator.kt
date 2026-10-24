package tiiehenry.android.app.snapshot.main.launch.makearchive

import android.content.Context
import android.text.format.Formatter
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.config.AppConfigManager
import tiiehenry.android.app.snapshot.archive.manage.ArchiveManager
import tiiehenry.android.app.snapshot.archive.manage.RetentionPolicyExecutor
import tiiehenry.android.app.snapshot.archive.make.ArchiveMaker
import tiiehenry.android.app.snapshot.archive.make.commitSnapshot
import tiiehenry.android.app.snapshot.archive.MetaInfoHelper
import tiiehenry.android.app.snapshot.archive.restore.RestorePreflight
import tiiehenry.android.app.snapshot.config.CompressItems
import tiiehenry.android.snapshot.fs.IFileType
import java.nio.file.Paths
import java.io.IOException
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.app.snapshot.main.launch.exception.ArchiveFailedException
import tiiehenry.android.app.snapshot.main.launch.makearchive.progress.IItemProgressDialog
import tiiehenry.android.app.snapshot.main.launch.makearchive.progress.ItemProgressDialog
import tiiehenry.android.app.snapshot.utils.AppStatusHelper
import tiiehenry.android.snapshot.file.ICompressCallback
import tiiehenry.android.snapshot.fs.CompressState
import tiiehenry.android.snapshot.task.ITaskHandler
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.iterator

/**
 * 快照创建管理类
 * 负责应用快照的创建流程
 */
class SnapshotCreator(
    private val context: Context,
    private val viewModelScope: CoroutineScope
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
    fun createSnapshot(item: ArchivedApp, group: SnapGroup, callback: Callback? = null) {
        val loadingDialog = ItemProgressDialog(context)
        loadingDialog.setItemMessage(context.getString(R.string.progress_creating_archive))
        loadingDialog.setItemStatus(context.getString(R.string.ellipsis))
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
        loadingDialog: IItemProgressDialog,
        item: ArchivedApp,
        group: SnapGroup,
        isCanceled: AtomicBoolean,
        callback: Callback? = null,
        callerHoldsPackageOp: Boolean = false
    ) {
        val failed = AtomicBoolean(false)
        val errorMessage = AtomicReference<String?>(null)

        val reportError = { msg: Exception ->
            failed.set(true)
            errorMessage.compareAndSet(null, msg.message)
            loadingDialog.setItemException(msg)
        }
        val reportErrorMessage = { msg: String ->
            reportError(ArchiveFailedException(msg))
        }

        viewModelScope.launch(Dispatchers.Default) {
            val packageDir = item.packageDir
            val guard = tiiehenry.android.app.snapshot.repository.AppDataRepository.getInstance().packageOpGuard
            val packageOp = beginSnapshotPackageOp(
                packageDir,
                callerHoldsPackageOp,
                guard::tryBeginPackageOp
            )
            if (!snapshotPackagingAllowed(packageOp)) {
                withContext(Dispatchers.Main) {
                    val ex = ArchiveFailedException(
                        context.getString(R.string.batch_operation_in_progress)
                    )
                    reportError(ex)
                    callback?.onError(ex)
                    callback?.onFinish()
                }
                return@launch
            }
            var unfinishedSnapshotDir: String? = null
            try {
                val snapShotApp = SnapshotApp.getInstance()
                val fs = snapShotApp.fileSystem
                val appManager = snapShotApp.appManager

                // 获取应用配置（使用 AppConfigManager 复用实例）
                val appConfig = AppConfigManager.getInstance().getConfig(
                    item.appInfo.packageName,
                    item.appInfo.userId
                )
                val groupConfig = group.config
                val packageName = item.appInfo.packageName
                val userId = group.userId
                Log.i(
                    TAG,
                    "createSnapshot pkg=$packageName user=$userId dir=${item.packageDir} group=${group.id}"
                )

                // 挂起应用，减少备份期间数据变更（不做 forceStop）
                if (!AppStatusHelper.preparePackageForSnapshot(packageName, userId)) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.snapshot_suspend_failed, packageName),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                // 创建压缩回调
                val compressCallback =
                    createCompressCallback(context, loadingDialog, failed, reportErrorMessage)

                val snapshotTasks = ArchiveMaker.makeSnapshot(
                    fs, appManager, item, item.appInfo, compressCallback, groupConfig, appConfig
                )
                var currentIndex = 0
                if (snapshotTasks != null) {
                    unfinishedSnapshotDir = snapshotTasks.dir
                    val tasks = snapshotTasks.tasks
                    val totalTask = tasks.size
                    fun updateIndex(index: Int) {
                        loadingDialog.setItemProgress(index * 100 / totalTask)
                    }

                    suspend fun failAndAbort(reason: String) {
                        Log.e(TAG, "snapshot aborted: $reason")
                        fs.delete(snapshotTasks.dir)
                        withContext(Dispatchers.Main) {
                            val ex = ArchiveFailedException(
                                errorMessage.get() ?: reason
                            )
                            if (!failed.get()) {
                                reportError(ex)
                            } else {
                                // 已通过回调写入对话框；仍要通知上层
                                loadingDialog.setItemException(ex)
                            }
                            callback?.onError(ex)
                        }
                    }

                    val metaHandler = tasks.remove("meta-info")
                        ?: throw ArchiveFailedException(context.getString(R.string.snapshot_metadata_failed))
                    val uninstallHandler = tasks.remove("uninstall-app")
                    // 执行其他任务
                    for (entry in tasks) {
                        if (isCanceled.get() || failed.get()) {
                            break
                        }
                        currentIndex++
                        withContext(Dispatchers.Main) {
                            if (failed.get()) return@withContext
                            updateIndex(currentIndex)
                            loadingDialog.setCurrentItem(entry.key)
                            loadingDialog.setItemMessage(
                                context.getString(R.string.progress_processing)
                            )
                            loadingDialog.setItemStatus("...")
                        }
                        if (failed.get()) {
                            break
                        }
                        entry.value.start()
                        if (failed.get() ||
                            entry.value.state() != CompressState.COMPRESS_STATE_COMPLETE
                        ) {
                            failAndAbort(
                                errorMessage.get()
                                    ?: "task ${entry.key} failed (state=${entry.value.state()})"
                            )
                            return@launch
                        }
                    }
                    if (failed.get()) {
                        failAndAbort(errorMessage.get() ?: context.getString(R.string.snapshot_payload_failed))
                        return@launch
                    }
                    if (isCanceled.get()) {
                        fs.delete(snapshotTasks.dir)
                        return@launch
                    }
                    var published = false
                    fun checkCanceled() {
                        if (isCanceled.get()) {
                            throw ArchiveFailedException(context.getString(R.string.group_batch_archive_cancelled))
                        }
                    }
                    try {
                        commitSnapshot(
                            writeMetadata = {
                                checkCanceled()
                                metaHandler.start()
                                if (metaHandler.state() != CompressState.COMPRESS_STATE_COMPLETE) {
                                    throw ArchiveFailedException(context.getString(R.string.snapshot_metadata_failed))
                                }
                            },
                            validate = {
                                checkCanceled()
                                val pending = Paths.get(snapshotTasks.dir, MetaInfoHelper.PENDING_META_INFO_FILE_NAME).toString()
                                val meta = MetaInfoHelper.read(fs, pending) ?: throw IOException(pending)
                                RestorePreflight.prepare(
                                    meta, snapshotTasks.dir, packageDir, null,
                                    meta.dataItems.orEmpty().contains("${CompressItems.COMPRESS_ITEM_APK}.json"),
                                    readItem = { name, dir -> MetaInfoHelper.readDataItem(name, dir) },
                                    verifyFile = { data, dir ->
                                        val path = RestorePreflight.childPath(dir, data.file)
                                        fs.fileType(path) == IFileType.TYPE_FILE &&
                                            fs.length(path) == data.targetSize &&
                                            fs.md5(path).equals(data.md5, ignoreCase = true)
                                    }
                                )
                            },
                            publish = {
                                checkCanceled()
                                if (!fs.move(snapshotTasks.dir, snapshotTasks.finalDir)) {
                                    throw IOException(snapshotTasks.finalDir)
                                }
                                val pending = Paths.get(snapshotTasks.finalDir, MetaInfoHelper.PENDING_META_INFO_FILE_NAME).toString()
                                val manifest = Paths.get(snapshotTasks.finalDir, MetaInfoHelper.META_INFO_FILE_NAME).toString()
                                if (!fs.move(pending, manifest)) {
                                    fs.delete(snapshotTasks.finalDir)
                                    throw IOException(manifest)
                                }
                                published = true
                                unfinishedSnapshotDir = null
                            },
                            afterCommit = {
                                checkCanceled()
                                uninstallHandler?.let {
                                    it.start()
                                    if (it.state() != CompressState.COMPRESS_STATE_COMPLETE) {
                                        throw ArchiveFailedException(context.getString(R.string.snapshot_uninstall_failed))
                                    }
                                }
                            }
                        )
                    } catch (e: Exception) {
                        // A failed uninstall must never delete an already committed backup.
                        if (!published) fs.delete(snapshotTasks.dir)
                        throw e
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
                        val ex = ArchiveFailedException("no task")
                        reportError(ex)
                        callback?.onError(ex)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    callback?.onError(e)
                    reportError(e)
                }
            } finally {
                unfinishedSnapshotDir?.let { pending ->
                    runCatching { SnapshotApp.getInstance().fileSystem.delete(pending) }
                }
                endSnapshotPackageOp(packageOp) { guard.endPackageOp(it) }
                val packageName = item.appInfo.packageName
                val userId = group.userId
                if (!AppStatusHelper.releasePackageAfterSnapshot(packageName, userId)) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.snapshot_unsuspend_failed, packageName),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
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
        loadingDialog: IItemProgressDialog,
        failed: AtomicBoolean,
        onErrorCallback: (String) -> Unit
    ): ICompressCallback {
        return object : ICompressCallback.Stub() {
            override fun onStart() {
                // 可选：显示开始状态
            }

            override fun onProgress(bytesWritten: Long, bytesPerS: Long) {
                if (failed.get()) return
                viewModelScope.launch(Dispatchers.Main) {
                    if (failed.get()) return@launch
                    val fileSize = Formatter.formatFileSize(context, bytesWritten)
                    loadingDialog.setItemMessage(
                        context.getString(R.string.progress_written, fileSize)
                    )
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
                val message = msg ?: "unknown"
                // 同步标记，避免后续任务继续；UI 更新仍切主线程
                failed.set(true)
                viewModelScope.launch(Dispatchers.Main) {
                    onErrorCallback(message)
                }
            }
        }
    }
}

internal sealed class SnapshotPackageOp {
    data object HeldByCaller : SnapshotPackageOp()
    data object Denied : SnapshotPackageOp()
    data class Acquired(val packageDir: String) : SnapshotPackageOp()
}

internal fun beginSnapshotPackageOp(
    packageDir: String,
    callerHoldsPackageOp: Boolean,
    tryBeginPackageOp: (String) -> Boolean
): SnapshotPackageOp {
    if (callerHoldsPackageOp) return SnapshotPackageOp.HeldByCaller
    return if (tryBeginPackageOp(packageDir)) {
        SnapshotPackageOp.Acquired(packageDir)
    } else {
        SnapshotPackageOp.Denied
    }
}

internal fun snapshotPackagingAllowed(op: SnapshotPackageOp): Boolean =
    op !is SnapshotPackageOp.Denied

internal fun endSnapshotPackageOp(
    op: SnapshotPackageOp,
    endPackageOp: (String) -> Unit
) {
    if (op is SnapshotPackageOp.Acquired) {
        endPackageOp(op.packageDir)
    }
}

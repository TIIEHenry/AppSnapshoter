package tiiehenry.android.app.snapshot.instance

import android.content.Context
import android.text.format.Formatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.archive.MetaInfoHelper
import tiiehenry.android.app.snapshot.archive.make.ArchiveMaker
import tiiehenry.android.app.snapshot.archive.make.commitSnapshot
import tiiehenry.android.app.snapshot.archive.restore.ArchiveRestorer
import tiiehenry.android.app.snapshot.archive.restore.RestorePreflight
import tiiehenry.android.app.snapshot.config.AppConfigManager
import tiiehenry.android.app.snapshot.config.CompressItems
import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.app.snapshot.main.launch.makearchive.progress.IItemProgressDialog
import tiiehenry.android.app.snapshot.repository.AppDataRepository
import tiiehenry.android.app.snapshot.utils.AppStatusHelper
import tiiehenry.android.snapshot.file.ICompressCallback
import tiiehenry.android.snapshot.fs.CompressState
import tiiehenry.android.snapshot.fs.IFileType
import tiiehenry.android.snapshot.task.ITaskHandler
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 固定实例：先记下名字和账号，再按普通快照把当前应用备份进 instances/。
 * 更新是再备份一次并替换这一份。普通存档的保留策略碰不到这里。
 */
class InstanceStore(
    private val context: Context,
    private val archivedApp: ArchivedApp,
    private val dialog: IItemProgressDialog,
) {
    private val fs = SnapshotApp.getInstance().fileSystem
    private val appManager = SnapshotApp.getInstance().appManager
    private val catalog = InstanceCatalog(fs, archivedApp.packageDir)
    private val packageName = archivedApp.appInfo.packageName
    private val userId = archivedApp.appInfo.userId

    suspend fun create(name: String, account: String, note: String): String = guarded {
        val info = requireInfo(name, account, note, exceptId = null)
        backup(existing = null, info = info)
        context.getString(R.string.instance_created, info.name)
    }

    suspend fun update(id: String): String = guarded {
        val current = catalog.read(id)
            ?: throw InstanceException(context.getString(R.string.instance_missing))
        backup(
            existing = current,
            info = InstanceInfo(current.name, current.account, current.note),
        )
        context.getString(R.string.instance_updated, current.name)
    }

    suspend fun edit(id: String, name: String, account: String, note: String): String = guarded {
        val current = catalog.read(id)
            ?: throw InstanceException(context.getString(R.string.instance_missing))
        val info = requireInfo(name, account, note, exceptId = id)
        catalog.writeCommitted(
            catalog.slotPath(id),
            current.copy(name = info.name, account = info.account, note = info.note),
        )
        context.getString(R.string.instance_edited, info.name)
    }

    suspend fun restore(id: String): String = guarded {
        requireInstalled()
        val current = catalog.read(id)
            ?: throw InstanceException(context.getString(R.string.instance_missing))
        status(context.getString(R.string.instance_progress_restore, current.name))
        val item = InstanceSlot.loadArchiveItem(
            fs,
            appManager,
            archivedApp,
            catalog.slotPath(id),
            current.name,
        )
        ArchiveRestorer.restoreArchiveSuspend(context, archivedApp, item)
        context.getString(R.string.instance_restored, current.name)
    }

    suspend fun delete(id: String): String = guarded {
        if (catalog.read(id) == null) {
            throw InstanceException(context.getString(R.string.instance_missing))
        }
        if (!catalog.deleteSlot(id)) {
            throw InstanceException(context.getString(R.string.instance_delete_failed))
        }
        context.getString(R.string.instance_deleted)
    }

    private suspend fun backup(existing: AppInstance?, info: InstanceInfo): AppInstance {
        requireInstalled()
        AppStatusHelper.preparePackageForSnapshot(packageName, userId)
        status(context.getString(R.string.instance_progress_save))
        val id = existing?.id ?: InstanceNames.newId()
        val now = System.currentTimeMillis()
        val instance = AppInstance(
            id = id,
            name = info.name,
            account = info.account,
            note = info.note,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        val committingDir = catalog.committingPath(id)
        if (fs.exists(committingDir) && !fs.delete(committingDir)) {
            throw InstanceException(context.getString(R.string.instance_save_failed))
        }
        var pendingDir: String? = null
        var ownsCommittingDir = false
        var published = false
        try {
            val relative = InstancePaths.ROOT_DIR + "/" + id + InstancePaths.COMMITTING_SUFFIX
            val failed = AtomicBoolean(false)
            val error = AtomicReference<String?>(null)
            val appConfig = AppConfigManager.getInstance().getConfig(packageName, userId)
            val tasks = ArchiveMaker.makeSnapshot(
                fs,
                appManager,
                archivedApp,
                archivedApp.appInfo,
                compressCallback(failed, error),
                archivedApp.group.config,
                appConfig,
                archiveName = relative,
            ) ?: throw InstanceException(context.getString(R.string.instance_save_failed))
            pendingDir = tasks.dir
            if (tasks.finalDir != committingDir) {
                throw InstanceException(context.getString(R.string.instance_save_failed))
            }
            val meta = runTasks(tasks.tasks, failed, error)
            commitSnapshot(
                writeMetadata = {
                    meta.start()
                    if (failed.get() || meta.state() != CompressState.COMPRESS_STATE_COMPLETE) {
                        throw InstanceException(error.get() ?: context.getString(R.string.instance_save_failed))
                    }
                    catalog.writeCommitted(tasks.dir, instance)
                },
                validate = {
                    val pendingManifest = Paths.get(tasks.dir, MetaInfoHelper.PENDING_META_INFO_FILE_NAME).toString()
                    val metadata = MetaInfoHelper.read(fs, pendingManifest)
                        ?: throw InstanceException(context.getString(R.string.instance_save_failed))
                    RestorePreflight.prepare(
                        metadata, tasks.dir, archivedApp.packageDir, null,
                        metadata.dataItems.orEmpty().contains("${CompressItems.COMPRESS_ITEM_APK}.json"),
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
                    // The payload was staged beside the package's normal archives,
                    // so instances/ may not exist until this first publication.
                    val instanceRoot = catalog.rootPath()
                    if (!(fs.mkdirs(instanceRoot) || fs.fileType(instanceRoot) == IFileType.TYPE_DIR) ||
                        !fs.move(tasks.dir, tasks.finalDir)) {
                        throw InstanceException(context.getString(R.string.instance_save_failed))
                    }
                    pendingDir = null
                    ownsCommittingDir = true
                    val pendingManifest = Paths.get(tasks.finalDir, MetaInfoHelper.PENDING_META_INFO_FILE_NAME).toString()
                    val manifest = Paths.get(tasks.finalDir, MetaInfoHelper.META_INFO_FILE_NAME).toString()
                    if (!fs.move(pendingManifest, manifest) || !catalog.publishCommit(id)) {
                        throw InstanceException(context.getString(R.string.instance_save_failed))
                    }
                    published = true
                },
                afterCommit = {},
            )
        } finally {
            pendingDir?.let { pending -> runCatching { fs.delete(pending) } }
            if (published) {
                catalog.reconcile(id)
            } else if (ownsCommittingDir || fs.exists(catalog.previousPath(id))) {
                catalog.reconcile(id)
            }
        }
        return instance
    }

    private suspend fun runTasks(
        tasks: LinkedHashMap<String, ITaskHandler>,
        failed: AtomicBoolean,
        error: AtomicReference<String?>,
    ): ITaskHandler {
        val pending = LinkedHashMap(tasks)
        val meta = pending.remove("meta-info")
            ?: throw InstanceException(context.getString(R.string.instance_save_failed))
        // 不启动快照配置里的「备份后卸载」。那是普通存档的任务，实例备份不执行它。
        pending.remove("uninstall-app")
        for ((name, task) in pending) {
            if (failed.get()) {
                throw InstanceException(error.get() ?: context.getString(R.string.instance_save_failed))
            }
            withContext(Dispatchers.Main) {
                dialog.setCurrentItem(name)
            }
            task.start()
            if (failed.get() || task.state() != CompressState.COMPRESS_STATE_COMPLETE) {
                throw InstanceException(error.get() ?: context.getString(R.string.instance_save_failed))
            }
        }
        if (failed.get()) {
            throw InstanceException(error.get() ?: context.getString(R.string.instance_save_failed))
        }
        return meta
    }

    private fun compressCallback(
        failed: AtomicBoolean,
        error: AtomicReference<String?>,
    ): ICompressCallback {
        return object : ICompressCallback.Stub() {
            override fun onStart() {}

            override fun onProgress(bytesWritten: Long, bytesPerS: Long) {
                if (failed.get()) return
                dialog.post {
                    val size = Formatter.formatFileSize(context, bytesWritten)
                    dialog.setItemMessage(context.getString(R.string.progress_written, size))
                    if (bytesPerS == 0L) {
                        dialog.setItemStatus(context.getString(R.string.ellipsis))
                    } else {
                        val speed = Formatter.formatFileSize(context, bytesPerS)
                        dialog.setItemStatus("$speed/s")
                    }
                }
            }

            override fun onDone(originSize: Long, targetSize: Long, md5: String) {}

            override fun onError(msg: String?) {
                failed.set(true)
                error.compareAndSet(null, msg ?: context.getString(R.string.instance_save_failed))
            }
        }
    }

    private fun requireInstalled() {
        if (!appManager.isInstalled(packageName, userId)) {
            throw InstanceException(context.getString(R.string.instance_not_installed))
        }
    }

    private fun requireInfo(name: String, account: String, note: String, exceptId: String?): InstanceInfo {
        return when (val check = InstanceNames.check(name, account, note, catalog.list(), exceptId)) {
            is InstanceInfoCheck.Ok -> check.info
            InstanceInfoCheck.NameInvalid ->
                throw InstanceException(context.getString(R.string.instance_name_invalid))
            InstanceInfoCheck.AccountInvalid ->
                throw InstanceException(context.getString(R.string.instance_account_invalid))
            InstanceInfoCheck.NoteInvalid ->
                throw InstanceException(context.getString(R.string.instance_note_invalid))
            InstanceInfoCheck.NameTaken ->
                throw InstanceException(context.getString(R.string.instance_name_taken))
            InstanceInfoCheck.AccountTaken ->
                throw InstanceException(context.getString(R.string.instance_account_taken))
        }
    }

    private suspend fun status(message: String) {
        withContext(Dispatchers.Main) {
            dialog.setItemMessage(message)
            dialog.setItemStatus(context.getString(R.string.ellipsis))
        }
    }

    private suspend fun <T> guarded(block: suspend InstanceStore.() -> T): T {
        val guard = AppDataRepository.getInstance().packageOpGuard
        val dir = archivedApp.packageDir
        if (guard.isGlobalBatchRunning() || !guard.tryBeginPackageOp(dir)) {
            throw InstanceException(context.getString(R.string.batch_operation_in_progress))
        }
        try {
            return withContext(Dispatchers.IO) { block() }
        } finally {
            guard.endPackageOp(dir)
            AppStatusHelper.releasePackageAfterSnapshot(packageName, userId)
        }
    }
}

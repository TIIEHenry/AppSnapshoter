package tiiehenry.android.app.snapshot.archive.manage

import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.archive.ArchivedApks
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.app.snapshot.instance.InstanceCatalog
import tiiehenry.android.app.snapshot.instance.InstancePaths
import tiiehenry.android.app.snapshot.repository.AppDataRepository
import java.io.File
import kotlin.io.path.absolutePathString
import java.nio.file.Paths

/**
 * 存档管理类
 * 负责存档的删除、清理等操作
 */
object ArchiveManager {

    private const val TAG = "ArchiveManager"

    /**
     * 删除单个存档
     * @param item 应用快照项
     * @param archiveItem 要删除的存档项
     * @return 是否删除成功
     */
    suspend fun deleteArchive(
        item: ArchivedApp,
        archiveItem: ArchiveItem,
        packageOpHeld: Boolean = false,
    ): Boolean = withPackageOp(item.packageDir, packageOpHeld, false) {
        val fs = SnapshotApp.getInstance().fileSystem
        deleteArchiveThenCleanup(
            delete = { fs.delete(archiveItem.path) },
            cleanup = { cleanupArchivedApks(item, archiveItem) }
        )
    }

    /**
     * 清理已归档的APK文件
     * @param item 应用快照项
     * @param archiveItem 存档项
     */
    private fun cleanupArchivedApks(item: ArchivedApp, archiveItem: ArchiveItem) {
        val archivedApkDirPath = ArchivedApks.getArchivedApkDir(
            item.packageDir,
            archiveItem.metaInfo.packageInfo.versionCode
        )
        val archivedApkDir = File(archivedApkDirPath)
        val archiveFiles = archivedApkDir.listFiles {
            it.name.substringBefore(".") == archiveItem.metaInfo.packageInfo.size.toString()
        }

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
        // If the instance catalog cannot be read, keep the APK: deleting a
        // shared payload that an instance may still need is worse than leaking.
        val isReferencedByInstance = try {
            InstanceCatalog(
                SnapshotApp.getInstance().fileSystem,
                item.packageDir,
            ).referencesApk(versionCode, size)
        } catch (e: Exception) {
            true
        }

        // 如果没有被引用，删除archiveFiles
        if (!isReferencedByOther && !isReferencedByInstance && archiveFiles != null) {
            archiveFiles.forEach { file ->
                try {
                    file.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 如果目录为空，删除目录
        if (archivedApkDir.list()?.isEmpty() == true) {
            archivedApkDir.delete()
        }
    }

    /**
     * 清空应用的所有存档（保留图标文件）
     * @param item 应用快照项
     * @return 是否清空成功
     */
    suspend fun clearAllArchives(item: ArchivedApp, packageOpHeld: Boolean = false): Boolean =
        withPackageOp(item.packageDir, packageOpHeld, false) {
        val fs = SnapshotApp.getInstance().fileSystem

        try {
            // 删除存档子目录，但保留图标文件
            val archiveNames = fs.listDir(item.packageDir)
            // Check the directory, not decoded metadata: a corrupt instance.json
            // must not make clear-all delete the shared APKs it still needs.
            val hasInstances = InstanceCatalog(fs, item.packageDir).hasSlots()
            val deleted = mutableListOf<Boolean>()
            for (archiveName in archiveNames) {
                if (archiveName == InstancePaths.ROOT_DIR) continue
                if (archiveName == "apks" && hasInstances) continue
                val archivePath = Paths.get(item.packageDir, archiveName).absolutePathString()
                // 跳过图标文件，只删除存档目录。跳过的路径不参与成败。
                if (archivePath != item.iconFile) {
                    deleted += fs.delete(archivePath)
                }
            }
            clearedArchivesSucceeded(deleted)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 完全删除应用及其所有数据
     * @param item 应用快照项
     * @param group 所属组
     * @return 是否删除成功
     */
    suspend fun deleteAppCompletely(
        item: ArchivedApp,
        group: SnapGroup,
        packageOpHeld: Boolean = false,
    ): Boolean = withPackageOp(item.packageDir, packageOpHeld, false) {
        val fs = SnapshotApp.getInstance().fileSystem

        try {
            val packageDeleted = fs.delete(item.packageDir)
            val iconInside = iconResidesInPackageDir(item.packageDir, item.iconFile)
            val iconDeleted = if (packageDeleted && !iconInside) fs.delete(item.iconFile) else true
            completeAppDeletionSucceeded(packageDeleted, iconInside, iconDeleted)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 重新加载应用的存档列表
     * @param item 应用快照项
     * @param forceReload 是否强制重新加载
     */
    suspend fun reloadArchives(item: ArchivedApp, forceReload: Boolean = true) {
        val fs = SnapshotApp.getInstance().fileSystem
        val appManager = SnapshotApp.getInstance().appManager
        try {
            item.loadArchives(fs, appManager, forceReload)
        }catch (e: Exception){
            e.printStackTrace()
        }
    }

    /**
     * 获取排序后的存档列表
     * @param item 应用快照项
     * @return 按创建时间降序排列的存档列表
     */
    fun getSortedArchives(item: ArchivedApp): List<ArchiveItem> {
        return item.archives.values.toList().sortedByDescending { it.metaInfo.makeTime }
    }

    /**
     * Serialize archive mutations with instance backup/restore on the same
     * packageDir. Callers already holding the guard (single snapshot, global
     * batch) pass [alreadyHeld] so this does not try to re-enter it.
     */
    private suspend fun <T> withPackageOp(
        packageDir: String,
        alreadyHeld: Boolean,
        busy: T,
        block: suspend () -> T,
    ): T {
        if (alreadyHeld) return block()
        val guard = AppDataRepository.getInstance().packageOpGuard
        if (guard.isGlobalBatchRunning() || !guard.tryBeginPackageOp(packageDir)) return busy
        return try {
            block()
        } finally {
            guard.endPackageOp(packageDir)
        }
    }
}

/** A failed filesystem delete leaves its APK references intact. */
internal fun deleteArchiveThenCleanup(delete: () -> Boolean, cleanup: () -> Unit): Boolean {
    val deleted = try { delete() } catch (_: Exception) { false }
    if (deleted) cleanup()
    return deleted
}

/** Empty means nothing had to be deleted, which is success. Any false fails the batch. */
internal fun clearedArchivesSucceeded(deleteResults: List<Boolean>): Boolean = deleteResults.all { it }

/**
 * A directory delete of false fails the removal.
 * An icon inside that directory is already gone when the directory delete succeeds,
 * so a later icon delete is not required.
 * An icon outside the directory succeeds only when that delete also returns true.
 */
internal fun completeAppDeletionSucceeded(
    packageDirDeleted: Boolean,
    iconInsidePackageDir: Boolean,
    iconDeleted: Boolean,
): Boolean {
    if (!packageDirDeleted) return false
    if (iconInsidePackageDir) return true
    return iconDeleted
}

internal fun iconResidesInPackageDir(packageDir: String, iconFile: String): Boolean {
    val root = Paths.get(packageDir).toAbsolutePath().normalize()
    val icon = Paths.get(iconFile).toAbsolutePath().normalize()
    return icon.startsWith(root)
}

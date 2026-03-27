package tiiehenry.android.app.snapshot.archieve.manage

import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.archieve.ArchivedApks
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.group.ArchivedApp
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
    suspend fun deleteArchive(item: ArchivedApp, archiveItem: ArchiveItem): Boolean {
        val fs = SnapshotApp.getInstance().fileSystem

        val success = try {
            // 删除指定的存档目录
            fs.delete(archiveItem.path)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }

        // 清理相关的APK文件
        cleanupArchivedApks(item, archiveItem)

        return success
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
            it.name.startsWith("${archiveItem.metaInfo.packageInfo.size}")
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
    suspend fun clearAllArchives(item: ArchivedApp): Boolean {
        val fs = SnapshotApp.getInstance().fileSystem

        return try {
            // 删除存档子目录，但保留图标文件
            val archiveNames = fs.listDir(item.packageDir)
            for (archiveName in archiveNames) {
                val archivePath = Paths.get(item.packageDir, archiveName).absolutePathString()
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
    }

    /**
     * 完全删除应用及其所有数据
     * @param item 应用快照项
     * @param group 所属组
     * @return 是否删除成功
     */
    suspend fun deleteAppCompletely(item: ArchivedApp, group: SnapGroup): Boolean {
        val fs = SnapshotApp.getInstance().fileSystem

        return try {
            // 完全删除应用目录和图标文件
            fs.delete(item.packageDir)
            fs.delete(item.iconFile)
            true
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
}

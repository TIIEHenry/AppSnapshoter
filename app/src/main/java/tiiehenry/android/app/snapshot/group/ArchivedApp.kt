package tiiehenry.android.app.snapshot.group

import android.util.Log
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.data.MetaInfoHelper
import tiiehenry.android.snapshot.app.IAppManager
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.fs.IFileType
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

data class ArchivedApp(val group: SnapGroup, val packageDir: String, val iconFile: String) {

    lateinit var appInfo: AppInfo

    val archives: LinkedHashMap<String, ArchiveItem> = LinkedHashMap()

    val latestArchive: ArchiveItem?
        get() = synchronized(archives) {
            archives.values.maxByOrNull { it.metaInfo.makeTime }
        }

    fun loadArchives(
        fs: IFileSystem,
        appManager: IAppManager,
        reload: Boolean
    ): LinkedHashMap<String, ArchiveItem> {
        val archiveNames = fs.listDir(packageDir).filter { it != "apks" }
        if (reload) {
            synchronized(archives) {
                archives.clear()
            }
        }
        Log.i("SnapedApp", "Loading archives: $archiveNames")
        for (archiveName in archiveNames) {
            if (!reload) {
                synchronized(archives) {
                    if (archives.get(archiveName) != null) {
                        continue
                    }
                }
            }
            val archiveDir = Paths.get(packageDir, archiveName).absolutePathString()
            if (fs.fileType(archiveDir) == IFileType.TYPE_FILE) {
                //json config
                continue
            }
            val jsonFile = Paths.get(archiveDir, MetaInfoHelper.META_INFO_FILE_NAME)
                .absolutePathString()
            if (fs.fileType(jsonFile) == IFileType.TYPE_FILE) {
                val metaInfo = MetaInfoHelper.read(fs, jsonFile)
                val appInfo = AppInfo(
                    fs,
                    appManager,
                    metaInfo.packageInfo.packageName,
                    group.userId,
                    metaInfo.packageInfo.versionName,
                    metaInfo.packageInfo.versionCode
                )
                appInfo.archiveLabel = metaInfo.packageInfo.label
                appInfo.archiveIconFile = iconFile
                val dataItems = MetaInfoHelper.readDataItems(
                    metaInfo.dataItems,
                    archiveDir
                )
                val extraItems =
                    metaInfo.extraItems.mapKeys { MetaInfoHelper.readDataItem(it.key, archiveDir) }
                val archiveItem =
                    ArchiveItem(metaInfo, appInfo, archiveName, archiveDir, dataItems, extraItems)
                synchronized(archives) {
                    archives[archiveName] = archiveItem
                }
            } else {
                Log.e("SnapedApp", "Invalid meta info file: $jsonFile")
            }
        }
        if (!this::appInfo.isInitialized) {
            latestArchive?.let {
                appInfo = it.appInfo
            }
        }
        return archives
    }

    val isRunning: Boolean
        get() {
            return appInfo.appManager.isPackageRunning(
                appInfo.packageName,
                appInfo.userId
            )
        }
}
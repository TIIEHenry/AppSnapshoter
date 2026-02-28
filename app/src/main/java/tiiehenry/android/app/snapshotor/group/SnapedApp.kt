package tiiehenry.android.app.snapshotor.group

import android.util.Log
import tiiehenry.android.app.snapshotor.app.AppInfo
import tiiehenry.android.app.snapshotor.archive.ArchiveItem
import tiiehenry.android.app.snapshotor.data.MetaInfoHelper
import tiiehenry.android.snapshotor.app.IAppManager
import tiiehenry.android.snapshotor.file.IFileSystem
import tiiehenry.android.snapshotor.fs.IFileType
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

data class SnapedApp(val packageDir: String, val iconFile: String) {

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
        val archiveNames = fs.listDir(packageDir)
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
            val jsonFile = Paths.get(archiveDir,  MetaInfoHelper.META_INFO_FILE_NAME)
                .absolutePathString()
            if (fs.fileType(jsonFile) == IFileType.TYPE_FILE) {
                val metaInfo = MetaInfoHelper.read(fs, jsonFile)
                val appInfo = AppInfo(
                    fs,
                    appManager,
                    metaInfo.packageInfo.packageName,
                    metaInfo.userId,
                    metaInfo.packageInfo.versionName,
                    metaInfo.packageInfo.versionCode
                )
                appInfo.archiveIconFile = iconFile
                val dataItems = MetaInfoHelper.readDataItems(
                    metaInfo.dataItems.filter { it != "apk.json" },
                    archiveDir
                )
                val archiveItem = ArchiveItem(metaInfo, appInfo, archiveName, archiveDir, dataItems)
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
}
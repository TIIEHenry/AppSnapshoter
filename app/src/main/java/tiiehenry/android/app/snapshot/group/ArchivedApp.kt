package tiiehenry.android.app.snapshot.group

import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.archive.MetaInfoHelper
import tiiehenry.android.app.snapshot.archive.bean.MetaDataItem
import tiiehenry.android.app.snapshot.archive.bean.MetaInfo
import tiiehenry.android.app.snapshot.instance.InstancePaths
import tiiehenry.android.snapshot.app.IAppManager
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.fs.IFileType
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

data class ArchivedApp(val group: SnapGroup, val packageDir: String, val iconFile: String) {

    lateinit var appInfo: AppInfo

    fun hasAppInfo(): Boolean = this::appInfo.isInitialized

    val archives: LinkedHashMap<String, ArchiveItem> = LinkedHashMap()

    private val completeArchiveNames = mutableSetOf<String>()

    /** False until every archive name from the last directory listing was read or intentionally skipped. */
    var archivesComplete: Boolean = false
        internal set

    val latestArchive: ArchiveItem?
        get() = synchronized(archives) {
            archives.values.maxByOrNull { it.metaInfo.makeTime }
        }

    fun loadArchives(
        fs: IFileSystem,
        appManager: IAppManager,
        reload: Boolean
    ): LinkedHashMap<String, ArchiveItem> {
        val archiveNames = try {
            fs.listDir(packageDir).filter {
                it != "apks" && it != InstancePaths.ROOT_DIR
            }
        } catch (e: Exception) {
            archivesComplete = false
            throw e
        }
        if (reload) {
            synchronized(archives) {
                archives.clear()
                completeArchiveNames.clear()
            }
        }
        GroupLog.i("SnapedApp", "Loading archives: $archiveNames")
        var failed = false
        for (archiveName in archiveNames) {
            val alreadyComplete = synchronized(archives) {
                !reload && archiveName in completeArchiveNames
            }
            if (alreadyComplete) continue
            val archiveDir = Paths.get(packageDir, archiveName).absolutePathString()
            try {
                if (fs.fileType(archiveDir) == IFileType.TYPE_FILE) {
                    continue
                }
                val jsonFile = Paths.get(archiveDir, MetaInfoHelper.META_INFO_FILE_NAME)
                    .absolutePathString()
                if (fs.fileType(jsonFile) != IFileType.TYPE_FILE) {
                    GroupLog.e("SnapedApp", "Invalid meta info file: $jsonFile")
                    continue
                }
                val metaInfo = metaReader(fs, jsonFile)
                if (metaInfo == null) {
                    GroupLog.e("SnapedApp", "Failed to parse meta info: $jsonFile")
                    continue
                }
                if (metaInfo.userId != group.userId) {
                    GroupLog.w(
                        "SnapedApp",
                        "Archive $archiveName userId=${metaInfo.userId} differs from group userId=${group.userId}"
                    )
                }
                val packageInfo = metaInfo.packageInfo
                val archiveAppInfo = AppInfo(
                    fs,
                    appManager,
                    packageInfo.packageName,
                    group.userId,
                    packageInfo.versionName,
                    packageInfo.versionCode
                )
                archiveAppInfo.archiveLabel = packageInfo.label
                archiveAppInfo.archiveIconFile = iconFile
                val dataItems = dataItemsReader(metaInfo.dataItems, archiveDir)
                val extraItems = readExtraItems(metaInfo.extraItems, archiveDir)
                val archiveItem = ArchiveItem(
                    metaInfo,
                    archiveAppInfo,
                    archiveName,
                    archiveDir,
                    dataItems,
                    extraItems
                )
                synchronized(archives) {
                    archives[archiveName] = archiveItem
                    completeArchiveNames.add(archiveName)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                failed = true
                synchronized(archives) {
                    archives.remove(archiveName)
                    completeArchiveNames.remove(archiveName)
                }
            }
        }
        archivesComplete = !failed
        if (!this::appInfo.isInitialized) {
            latestArchive?.let {
                appInfo = it.appInfo
            }
        }
        return archives
    }

    private fun readExtraItems(
        raw: Map<*, *>?,
        archiveDir: String
    ): Map<MetaDataItem, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        val parsed = LinkedHashMap<MetaDataItem, String>()
        for ((key, value) in raw) {
            if (key !is String || value !is String) continue
            try {
                parsed[extraItemReader(key, archiveDir)] = value
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return parsed
    }

    val isRunning: Boolean
        get() {
            val info = if (this::appInfo.isInitialized) appInfo else return false
            return info.appManager.isPackageRunning(
                info.packageName,
                info.userId
            )
        }

    companion object {
        internal var metaReader: (IFileSystem, String) -> MetaInfo? = { fs, path ->
            MetaInfoHelper.read(fs, path)
        }
        internal var dataItemsReader: (List<String>?, String) -> List<MetaDataItem> = { names, dir ->
            if (names.isNullOrEmpty()) emptyList() else MetaInfoHelper.readDataItems(names, dir)
        }
        internal var extraItemReader: (String, String) -> MetaDataItem = { name, dir ->
            MetaInfoHelper.readDataItem(name, dir)
        }

        internal fun resetArchiveReaders() {
            metaReader = { fs, path -> MetaInfoHelper.read(fs, path) }
            dataItemsReader = { names, dir ->
                if (names.isNullOrEmpty()) emptyList() else MetaInfoHelper.readDataItems(names, dir)
            }
            extraItemReader = { name, dir -> MetaInfoHelper.readDataItem(name, dir) }
        }
    }
}

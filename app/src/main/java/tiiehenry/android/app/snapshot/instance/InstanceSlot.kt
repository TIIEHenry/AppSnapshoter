package tiiehenry.android.app.snapshot.instance

import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.archive.MetaInfoHelper
import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.snapshot.app.IAppManager
import tiiehenry.android.snapshot.file.IFileSystem

object InstanceSlot {
    fun loadArchiveItem(
        fs: IFileSystem,
        appManager: IAppManager,
        archivedApp: ArchivedApp,
        slotDir: String,
        slotName: String,
    ): ArchiveItem {
        val metaFile = java.nio.file.Paths.get(slotDir, MetaInfoHelper.META_INFO_FILE_NAME)
            .toString()
        val meta = MetaInfoHelper.read(fs, metaFile)
            ?: throw InstanceException("missing meta")
        val pkgInfo = meta.packageInfo ?: throw InstanceException("missing package info")
        val live = archivedApp.appInfo
        val appInfo = AppInfo(
            fs,
            appManager,
            live.packageName,
            live.userId,
            pkgInfo.versionName,
            pkgInfo.versionCode,
        )
        appInfo.archiveLabel = live.archiveLabel
        appInfo.archiveIconFile = live.archiveIconFile
        val dataItems = MetaInfoHelper.readDataItems(meta.dataItems.orEmpty(), slotDir)
        val extraItems = try {
            meta.extraItems.orEmpty().mapKeys { (fileName, _) ->
                MetaInfoHelper.readDataItem(fileName, slotDir)
            }
        } catch (e: Exception) {
            emptyMap()
        }
        return ArchiveItem(meta, appInfo, slotName, slotDir, dataItems, extraItems)
    }
}

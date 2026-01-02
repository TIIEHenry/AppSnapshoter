package tiieherny.android.app.snapshotor.group

import tiiehenry.android.shapshotor.file.IFileSystem
import tiieherny.android.app.snapshotor.app.AppInfo
import tiieherny.android.app.snapshotor.archive.ArchieveItem

data class SnapedApp(val info: AppInfo) {

    val archives: MutableList<ArchieveItem> = mutableListOf()

    fun loadArchives(fileSystem: IFileSystem): List<ArchieveItem> {

        return archives
    }
}
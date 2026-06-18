package tiiehenry.android.app.snapshot.main.launch.batch

import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.group.ArchivedApp

object RestoreRecordWriter {

    fun onRestoreSuccess(archivedApp: ArchivedApp, archiveItem: ArchiveItem) {
        if (archiveItem.name.isBlank()) return
        RestoreRecordStore.put(
            group = archivedApp.group,
            packageName = archiveItem.appInfo.packageName,
            userId = archiveItem.appInfo.userId,
            record = RestoreRecord(
                restoredAt = System.currentTimeMillis(),
                archiveName = archiveItem.name,
                archiveMakeTime = archiveItem.metaInfo.makeTime
            )
        )
    }
}

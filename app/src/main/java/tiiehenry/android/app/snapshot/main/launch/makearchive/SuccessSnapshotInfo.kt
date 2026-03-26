package tiiehenry.android.app.snapshot.main.launch.makearchive

import tiiehenry.android.app.snapshot.group.ArchivedApp

data class SuccessSnapshotInfo(
    val archivedApp: ArchivedApp,
    val timeMillis: Long,
    val archiveSize: Long
)
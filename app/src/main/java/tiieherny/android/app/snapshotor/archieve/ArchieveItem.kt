package tiieherny.android.app.snapshotor.archive

import tiieherny.android.app.snapshotor.app.AppInfo

data class ArchieveItem(
    val appInfo: AppInfo,
    val name: String,
    val path: String,
    val createTime: Long,
    val size: Long = 0
)

package tiieherny.android.app.snapshotor.archive

import tiieherny.android.app.snapshotor.app.AppInfo
import tiieherny.android.app.snapshotor.data.MetaInfo

data class ArchiveItem(
    val metaInfo: MetaInfo,
    val appInfo: AppInfo,
    val name: String,
    val path: String,
)

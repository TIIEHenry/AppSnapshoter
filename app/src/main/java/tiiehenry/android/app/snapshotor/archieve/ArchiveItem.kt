package tiiehenry.android.app.snapshotor.archive

import tiiehenry.android.app.snapshotor.app.AppInfo
import tiiehenry.android.app.snapshotor.data.MetaInfo

data class ArchiveItem(
    val metaInfo: MetaInfo,
    val appInfo: AppInfo,
    val name: String,
    val path: String,
)

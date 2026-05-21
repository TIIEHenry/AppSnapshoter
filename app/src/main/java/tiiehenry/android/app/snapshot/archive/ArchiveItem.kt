package tiiehenry.android.app.snapshot.archive

import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.archive.bean.MetaDataItem
import tiiehenry.android.app.snapshot.archive.bean.MetaInfo

data class ArchiveItem(
    val metaInfo: MetaInfo,
    val appInfo: AppInfo,
    val name: String,
    val path: String,
    val dataItems: List<MetaDataItem>,
    val extraItems: Map<MetaDataItem, String> = emptyMap()
)

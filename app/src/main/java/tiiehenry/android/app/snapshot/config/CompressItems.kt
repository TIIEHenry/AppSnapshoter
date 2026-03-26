package tiiehenry.android.app.snapshot.config

import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.data.bean.MetaDataItem

object CompressItems {
    // 压缩项常量
    const val COMPRESS_ITEM_APK = "apk"
    const val COMPRESS_ITEM_DATA = "data"
    const val COMPRESS_ITEM_USER = "user"
    const val COMPRESS_ITEM_USER_DE = "user_de"
    const val COMPRESS_ITEM_OBB = "obb"
    const val COMPRESS_ITEM_MEDIA = "media"

    @JvmStatic
    val all = setOf(
        COMPRESS_ITEM_APK,
        COMPRESS_ITEM_DATA,
        COMPRESS_ITEM_USER,
        COMPRESS_ITEM_USER_DE,
        COMPRESS_ITEM_OBB,
        COMPRESS_ITEM_MEDIA
    )

    /**
     * 获取存档中所有可恢复的项目（包括标准项目和额外项目）
     */
    fun getAllRestoreOptions(archiveItem: ArchiveItem): List<MetaDataItem> {
        val options = mutableListOf<MetaDataItem>()
        
        // 添加标准数据项
        options.addAll(archiveItem.dataItems)
        
        // 添加额外项目
        archiveItem.extraItems.forEach { (dataItem, path) ->
            options.add(dataItem)
        }
        
        return options
    }
}
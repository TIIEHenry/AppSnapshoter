package tiiehenry.android.app.snapshot.config

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
}
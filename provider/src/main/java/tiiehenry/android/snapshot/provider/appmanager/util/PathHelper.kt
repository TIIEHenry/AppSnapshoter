package tiiehenry.android.snapshot.provider.appmanager.util

/**
 *路工具类，提供应用相关路径的获取方法
 */
object PathHelper {
    private const val DATA_MEDIA = "/data/media"
    const val TMP_PARCEL_PREFIX = "tmp_parcel_"
    const val TMP_SUFFIX = ".tmp"

    /**
     * 获取应用的用户数据目录
     */
    fun getAppUserDir(userId: Int, packageName: String): String {
        return if (userId == 0) {
            "/data/user/0/$packageName"
        } else {
            "/data/user/$userId/$packageName"
        }
    }

    /**
     * 获取应用的用户DE数据目录
     */
    fun getAppUserDeDir(userId: Int, packageName: String): String {
        return if (userId == 0) {
            "/data/user_de/0/$packageName"
        } else {
            "/data/user_de/$userId/$packageName"
        }
    }

    /**
     * 获取应用的数据目录
     */
    fun getAppDataDir(userId: Int, packageName: String): String {
        return "$DATA_MEDIA/$userId/Android/data/$packageName"
    }

    /**
     * 获取应用的OBB目录
     */
    fun getAppObbDir(userId: Int, packageName: String): String {
        return "$DATA_MEDIA/$userId/Android/obb/$packageName"
    }

    /**
     * 获取应用的媒体目录
     */
    fun getAppMediaDir(userId: Int, packageName: String): String {
        return "$DATA_MEDIA/$userId/Android/media/$packageName"
    }

    /**
     * 获取应用的缓存目录
     */
    fun getAppExCacheDir(userId: Int, packageName: String): String {
        return "$DATA_MEDIA/$userId/Android/data/$packageName/cache"
    }
}
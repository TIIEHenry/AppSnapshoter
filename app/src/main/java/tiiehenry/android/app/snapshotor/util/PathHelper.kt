package tiiehenry.android.app.snapshotor.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import android.os.Environment

/**
 * 路径工具类，提供应用相关路径的获取方法
 */
object PathHelper {
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
        return "${Environment.getExternalStorageDirectory()}/Android/data/$packageName"
    }

    /**
     * 获取应用的OBB目录
     */
    fun getAppObbDir(userId: Int, packageName: String): String {
        return "${Environment.getExternalStorageDirectory()}/Android/obb/$packageName"
    }

    /**
     * 获取应用的媒体目录
     */
    fun getAppMediaDir(userId: Int, packageName: String): String {
        return "${Environment.getExternalStorageDirectory()}/Android/media/$packageName"
    }

    /**
     * 获取应用的缓存目录
     */
    fun getAppCacheDir(userId: Int, packageName: String): String {
        return "${Environment.getExternalStorageDirectory()}/Android/data/$packageName/cache"
    }

    /**
     * 将 URI 转换为绝对路径
     */
    fun uriToAbsolutePath(context: Context, uri: Uri): String {
        val documentFile = DocumentFile.fromTreeUri(context, uri)
        if (documentFile != null) {
            val realPath = getRealPathFromUri(uri)
            if (realPath.isNotEmpty()) {
                return realPath
            }
        }
        return uri.toString()
    }

    /**
     * 从 URI 获取真实文件路径
     */
    fun getRealPathFromUri(uri: Uri): String {
        val docId = DocumentsContract.getTreeDocumentId(uri)

        // 处理 primary 存储（内部存储）
        if (docId.startsWith("primary:")) {
            val path = docId.substringAfter("primary:")
            return "/storage/emulated/0/$path"
        }

        // 处理 SD 卡等外部存储
        if (docId.contains(":")) {
            val (storageId, path) = docId.split(":", limit = 2)
            return "/storage/$storageId/$path"
        }

        return ""
    }
}
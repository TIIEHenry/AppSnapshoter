package tiiehenry.android.app.snapshot.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

/**
 * 路径工具类，提供应用相关路径的获取方法
 */
object PathHelper {
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
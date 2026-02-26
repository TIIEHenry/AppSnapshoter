package tiiehenry.android.snapshotor.provider.filesystem

import java.io.File
import java.security.MessageDigest

object MD5Utils {
    /**
     * 计算文件的MD5值
     * @param file 要计算MD5的文件
     * @return MD5字符串，如果计算失败返回null
     */
    fun getFileMD5(file: File): String? {
        if (!file.exists() || !file.isFile) {
            return null
        }
        
        return try {
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 计算文件的MD5值
     * @param filePath 文件路径
     * @return MD5字符串，如果计算失败返回null
     */
    fun getFileMD5(filePath: String): String? {
        return getFileMD5(File(filePath))
    }
}

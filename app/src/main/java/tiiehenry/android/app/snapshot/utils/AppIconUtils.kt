package tiiehenry.android.app.snapshot.utils

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.util.Log
import com.bumptech.glide.Glide
import tiiehenry.android.snapshot.app.AppPermission
import tiiehenry.android.snapshot.app.IAppManager
import tiiehenry.android.snapshot.file.IFileSystem
import java.io.ByteArrayOutputStream

/**
 * 应用图标工具类
 */
object AppIconUtils {

    /**
     * 加载并保存应用图标
     * @param context Context
     * @param fs 文件系统
     * @param appManager 应用管理器
     * @param packageName 包名
     * @param userId 用户 ID
     * @param iconFile 图标文件路径
     * @return 是否成功加载并保存图标
     */
    fun loadAndSaveAppIcon(
        context: Context,
        fs: IFileSystem,
        appManager: IAppManager,
        packageName: String,
        userId: Int,
        iconFile: String
    ): Boolean {
        return try {
            val icon = appManager.loadIcon(packageName, userId)
            if (icon != null) {
                val success = saveIconToFile(fs, icon, iconFile)
                if (success) {
                    Log.d("AppIconUtils", "Saved icon to: $iconFile")
                    // 立即清除 Glide 缓存，确保下次加载时使用新图标
                    Glide.get(context).clearDiskCache()
                    Glide.get(context).clearMemory()
                } else {
                    Log.w("AppIconUtils", "Failed to save icon for $packageName")
                }
                success
            } else {
                Log.w("AppIconUtils", "Failed to load icon for $packageName")
                false
            }
        } catch (e: Exception) {
            Log.e("AppIconUtils", "Error loading icon for $packageName: ${e.message}", e)
            false
        }
    }

    /**
     * 保存应用图标到指定路径
     * @param fs 文件系统
     * @param bitmap 图标位图
     * @param filePath 保存路径
     * @return 是否保存成功
     */
    private fun saveIconToFile(fs: IFileSystem, bitmap: Bitmap, filePath: String): Boolean {
        return try {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            val byteArray = outputStream.toByteArray()

            fs.openFile(
                filePath,
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_WRITE_ONLY
            ).use { pfd ->
                ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { fos ->
                    fos.write(byteArray)
                    fos.flush()
                }
            }
            true
        } catch (e: Exception) {
            Log.e("AppIconUtils", "Failed to save icon to $filePath: ${e.message}", e)
            false
        }
    }
}

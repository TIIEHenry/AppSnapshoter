package tiiehenry.android.app.snapshotor.util

import android.content.pm.ApplicationInfo
import tiiehenry.android.snapshotor.file.IFileSystem

/**
 * APK 工具类
 */
object ApkUtil {

    /**
     * 计算已安装 APK 的总大小（包含主 APK 和 split APK）
     *
     * @param fs 文件系统接口
     * @param appInfo 应用信息
     * @return APK 总大小（字节）
     */
    fun calculateInstalledApkSize(fs: IFileSystem, appInfo: ApplicationInfo): Long {
        var totalSize = 0L
        // 主 APK
        appInfo.sourceDir?.let { path ->
            totalSize += fs.length(path)
        }
        // Split APK
        appInfo.splitSourceDirs?.forEach { path ->
            totalSize += fs.length(path)
        }
        return totalSize
    }
}

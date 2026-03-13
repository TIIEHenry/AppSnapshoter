package tiiehenry.android.app.snapshot.util

import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.group.SnapedApp
import tiiehenry.android.app.snapshot.model.PackageStatus
import tiiehenry.android.snapshot.app.IAppManager

/**
 * 应用状态帮助类
 * 负责判断应用的安装状态、运行状态、版本状态等
 */
object AppStatusHelper {

    /**
     * 获取应用包状态
     * @param item 应用快照项
     * @return 应用状态
     */
    fun getPackageStatus(item: SnapedApp): PackageStatus {
        val appManager = item.appInfo.appManager
        val packageName = item.appInfo.packageName
        val userId = item.appInfo.userId

        // 检查应用是否安装
        val isInstalled = try {
            appManager.isInstalled(packageName, userId)
        } catch (e: Exception) {
            false
        }

        if (!isInstalled) {
            return PackageStatus.NOT_INSTALLED
        }

        // 获取存档中最新版本的versionCode
        val latestArchiveVersion = item.latestArchive?.metaInfo?.packageInfo?.versionCode

        // 如果没有存档，返回已安装状态
        if (latestArchiveVersion == null) {
            return PackageStatus.INSTALLED
        }

        // 获取已安装应用的versionCode
        val installedVersion = try {
            val packageInfo = appManager.getPackageInfo(packageName, 0, userId)
            packageInfo?.longVersionCode ?: 0L
        } catch (e: Exception) {
            0L
        }

        // 比较版本：存档版本高于已安装版本表示可更新
        return if (latestArchiveVersion != installedVersion) {
            PackageStatus.CAN_UPDATE
        } else {
            PackageStatus.INSTALLED
        }
    }

    /**
     * 检查应用是否已安装
     * @param item 应用快照项
     * @return 是否已安装
     */
    fun isAppInstalled(item: SnapedApp): Boolean {
        return try {
            item.appInfo.appManager.isInstalled(
                item.appInfo.packageName,
                item.appInfo.userId
            )
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查应用是否正在运行
     * @param item 应用快照项
     * @return 是否正在运行
     */
    fun isAppRunning(item: SnapedApp): Boolean {
        return try {
            item.appInfo.appManager.isPackageRunning(
                item.appInfo.packageName,
                item.appInfo.userId
            )
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 启动应用
     * @param packageName 包名
     * @param userId 用户ID
     * @return 是否启动成功
     */
    fun launchApp(packageName: String, userId: Int): Boolean {
        return try {
            val appManager = SnapshotApp.getInstance().appManager
            appManager.launchApp(packageName, userId)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 挂起应用
     * @param packageName 包名
     * @param userId 用户ID
     */
    fun suspendPackage(packageName: String, userId: Int) {
        try {
            val appManager = SnapshotApp.getInstance().appManager
            appManager.suspendPackage(packageName, userId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 恢复挂起应用
     * @param packageName 包名
     * @param userId 用户ID
     */
    fun unsuspendPackage(packageName: String, userId: Int) {
        try {
            val appManager = SnapshotApp.getInstance().appManager
            appManager.unsuspendPackage(packageName, userId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

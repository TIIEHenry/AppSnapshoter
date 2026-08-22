package tiiehenry.android.app.snapshot.utils

import android.util.Log
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.app.snapshot.main.launch.group.item.PackageStatus
import tiiehenry.android.snapshot.app.IAppManager

/**
 * 应用状态帮助类
 * 负责判断应用的安装状态、运行状态、版本状态等
 */
object AppStatusHelper {

    private const val TAG = "AppStatusHelper"

    /**
     * 获取应用包状态
     * @param item 应用快照项
     * @return 应用状态
     */
    fun getPackageStatus(item: ArchivedApp): PackageStatus {
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
    fun isAppInstalled(item: ArchivedApp): Boolean {
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
    fun isAppRunning(item: ArchivedApp): Boolean {
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
     * 快照前准备：仅挂起应用（不做 forceStop，避免部分机型/银行包在强停后 APK 路径短暂不可读）。
     * @return 挂起是否成功
     */
    fun preparePackageForSnapshot(packageName: String, userId: Int): Boolean {
        val appManager = SnapshotApp.getInstance().appManager
        if (!isInstalledSafe(appManager, packageName, userId)) {
            Log.w(TAG, "preparePackageForSnapshot skipped, not installed: $packageName user=$userId")
            return false
        }

        val suspendOk = runCatching { appManager.suspendPackage(packageName, userId) }
            .getOrDefault(false)
        if (suspendOk) {
            AppSuspendTracker.markSuspended(packageName, userId)
        } else {
            Log.w(TAG, "suspendPackage failed: $packageName user=$userId")
        }
        return suspendOk
    }

    /**
     * 快照结束后恢复挂起状态。
     * @return 解冻是否成功；若本流程未挂起该包则返回 true
     */
    fun releasePackageAfterSnapshot(packageName: String, userId: Int): Boolean {
        if (!AppSuspendTracker.isTracked(packageName, userId)) {
            return true
        }

        val appManager = SnapshotApp.getInstance().appManager
        if (!isInstalledSafe(appManager, packageName, userId)) {
            AppSuspendTracker.markReleased(packageName, userId)
            return true
        }

        val unsuspendOk = runCatching { appManager.unsuspendPackage(packageName, userId) }
            .getOrDefault(false)
        if (unsuspendOk) {
            AppSuspendTracker.markReleased(packageName, userId)
        } else {
            Log.e(TAG, "unsuspendPackage failed: $packageName user=$userId")
        }
        return unsuspendOk
    }

    /**
     * 启动时兜底：尝试解冻上次异常退出时遗留的挂起记录。
     */
    fun recoverOrphanedSuspensions(): Int {
        var recovered = 0
        for ((packageName, userId) in AppSuspendTracker.pendingEntries()) {
            if (releasePackageAfterSnapshot(packageName, userId)) {
                recovered++
            }
        }
        if (recovered > 0) {
            Log.i(TAG, "Recovered $recovered orphaned suspended package(s)")
        }
        return recovered
    }

    private fun isInstalledSafe(
        appManager: IAppManager,
        packageName: String,
        userId: Int
    ): Boolean {
        return runCatching { appManager.isInstalled(packageName, userId) }.getOrDefault(false)
    }
}

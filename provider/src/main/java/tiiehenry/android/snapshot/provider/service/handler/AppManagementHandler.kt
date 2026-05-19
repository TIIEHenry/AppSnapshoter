package tiiehenry.android.snapshot.provider.service.handler

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManagerHidden
import android.content.pm.UserInfo
import android.graphics.Bitmap
import android.app.ActivityManagerHidden
import android.os.UserManagerHidden

/**
 * 应用管理处理器 - 保留以兼容现有代码
 * 内部职责已拆分为：
 * - [PackageManagerDelegate] 包查询、安装、卸载
 * - [ProcessManager] force-stop、clear data、suspend/unsuspend
 * - [AppLauncher] 应用启动
 */
class AppManagementHandler(
    context: Context,
    mPackageManager: PackageManager,
    mPackageManagerHidden: PackageManagerHidden,
    mUserManager: UserManagerHidden,
    mActivityManagerHidden: ActivityManagerHidden
) {

    private val packageManagerDelegate = PackageManagerDelegate(
        mPackageManager, mPackageManagerHidden, mUserManager
    )
    private val processManager = ProcessManager(mActivityManagerHidden)
    private val appLauncher = AppLauncher(processManager)

    fun getInstalledAppInfos(): List<tiiehenry.android.snapshot.app.AppInfo> =
        packageManagerDelegate.getInstalledAppInfos()

    fun getInstalledAppStorages(): List<tiiehenry.android.snapshot.app.AppStorage> =
        packageManagerDelegate.getInstalledAppStorages()

    fun getUsers(): List<UserInfo> = packageManagerDelegate.getUsers()

    fun getPackageSourceDir(packageName: String, userId: Int): List<String> =
        packageManagerDelegate.getPackageSourceDir(packageName, userId)

    fun getPackageInfo(packageName: String, flags: Int, userId: Int): PackageInfo? =
        packageManagerDelegate.getPackageInfo(packageName, flags, userId)

    fun getApplicationInfo(packageName: String, flags: Int, userId: Int): ApplicationInfo? =
        packageManagerDelegate.getApplicationInfo(packageName, flags, userId)

    fun loadLabel(packageName: String, userId: Int): String? =
        packageManagerDelegate.loadLabel(packageName, userId)

    fun loadIcon(packageName: String, userId: Int): Bitmap? =
        packageManagerDelegate.loadIcon(packageName, userId)

    fun isInstalled(packageName: String, userId: Int): Boolean =
        packageManagerDelegate.isInstalled(packageName, userId)

    fun installApk(file: String, userId: Int): Boolean =
        packageManagerDelegate.installApk(file, userId)

    fun installApks(files: List<String>, userId: Int): Boolean =
        packageManagerDelegate.installApks(files, userId)

    fun uninstallApk(packageName: String, userId: Int): Boolean =
        packageManagerDelegate.uninstallApk(packageName, userId)

    fun forceStopPackage(packageName: String, userId: Int): Boolean =
        processManager.forceStopPackage(packageName, userId)

    fun clearAppData(packageName: String, userId: Int): Boolean =
        processManager.clearAppData(packageName, userId)

    fun suspendPackage(packageName: String, userId: Int): Boolean =
        processManager.suspendPackage(packageName, userId)

    fun unsuspendPackage(packageName: String, userId: Int): Boolean =
        processManager.unsuspendPackage(packageName, userId)

    fun isPackageRunning(packageName: String, userId: Int): Boolean =
        processManager.isPackageRunning(packageName, userId)

    fun launchApp(packageName: String, userId: Int): Boolean =
        appLauncher.launchApp(packageName, userId)
}

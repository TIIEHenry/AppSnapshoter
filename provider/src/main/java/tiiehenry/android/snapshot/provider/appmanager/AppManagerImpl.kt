package tiiehenry.android.snapshot.provider.appmanager

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.UserInfo
import android.graphics.Bitmap
import android.os.UserHandle
import android.util.Log
import kotlinx.coroutines.runBlocking
import tiiehenry.android.snapshot.app.AppInfo
import tiiehenry.android.snapshot.provider.utils.drawableToBitmap
import tiiehenry.android.snapshot.app.AppPermission
import tiiehenry.android.snapshot.app.AppStorage
import tiiehenry.android.snapshot.app.IAppManager
import tiiehenry.android.snapshot.app.UserInfoHide
import tiiehenry.android.snapshot.provider.appmanager.util.LogHelper
import tiiehenry.android.snapshot.provider.appmanager.util.PathHelper
import tiiehenry.android.snapshot.provider.service.ISnapShotRootService
import tiiehenry.android.snapshot.provider.service.SnapShotRootServiceClient
import java.lang.reflect.Field

class AppManagerImpl(
    private val context: Context,
    private val rootServiceClient: SnapShotRootServiceClient
) : IAppManager {

    private val packageManager: PackageManager = context.packageManager
    private val appInfos = mutableListOf<AppInfo>()

    private fun rootService(): ISnapShotRootService {
        return rootServiceClient.client!!
    }

    private inline fun <T> safeCall(method: String, default: T, block: ISnapShotRootService.() -> T): T {
        return try {
            rootService().block()
        } catch (e: Exception) {
            LogHelper.e(TAG, method, "Failed", e)
            default
        }
    }

    private inline fun safeRun(method: String, block: ISnapShotRootService.() -> Unit) {
        try {
            rootService().block()
        } catch (e: Exception) {
            LogHelper.e(TAG, method, "Failed", e)
        }
    }

    // ==================== 应用信息 ====================

    private fun fetchInstalledAppInfos(): List<AppInfo> {
        val infos = safeCall("fetchInstalledAppInfos", mutableListOf()) {
            getInstalledAppInfos()
        }
        infos.removeIf { it.packageName == context.packageName }
        synchronized(appInfos) {
            appInfos.clear()
            appInfos.addAll(infos)
        }
        return infos
    }

    private fun getInstalledAppInfosCached(): List<AppInfo> {
        if (appInfos.isEmpty()) {
            fetchInstalledAppInfos()
        }
        return appInfos
    }

    override fun getUsers(): List<UserInfoHide> {
        return safeCall("getUsers", emptyList()) { getUsers() }
            .map { UserInfoHide(it.id, it.name ?: "") }
    }

    override fun getInstalledPackages(flags: Int, userId: Int): List<String> {
        fetchInstalledAppInfos()
        return getInstalledAppInfosCached().map { it.packageName }
    }

    override fun getPackageInfo(packageName: String, flags: Int, userId: Int): PackageInfo? {
        return try {
            runBlocking { safeCall("getPackageInfo", null) { getPackageInfo(packageName, flags, userId) } }
        } catch (e: Exception) {
            LogHelper.w(TAG, "getPackageInfo", "Falling back to PackageManager", e)
            packageManager.getPackageInfo(packageName, flags)
        }
    }

    override fun getApplicationInfo(packageName: String, flags: Int, userId: Int): ApplicationInfo? {
        return runBlocking { safeCall("getApplicationInfo", null) { getApplicationInfo(packageName, flags, userId) } }
    }

    override fun loadLabel(packageName: String, userId: Int): String? {
        return runBlocking { safeCall("loadLabel", null) { loadLabel(packageName, userId) } }
    }

    override fun loadIcon(packageName: String, userId: Int): Bitmap? {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val drawable = packageManager.getApplicationIcon(appInfo)
            drawableToBitmap(drawable)
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }

    override fun getDir(packageName: String, userId: Int, type: Int): String? {
        return try {
            when (type) {
                DIR_TYPE_DATA -> PathHelper.getAppDataDir(userId, packageName)
                DIR_TYPE_SOURCE -> PathHelper.getAppUserDir(userId, packageName)
                DIR_TYPE_NATIVE -> PathHelper.getAppUserDir(userId, packageName) + "/lib"
                else -> null
            }
        } catch (e: Exception) {
            LogHelper.e(TAG, "getDir", "Failed to get directory", e)
            null
        }
    }

    // ==================== 权限 ====================

    override fun getPermissions(packageName: String, userId: Int): List<AppPermission?> {
        return runBlocking { safeCall("getPermissions", emptyList()) { getPermissions(packageName, userId) } }
    }

    override fun setAppPermission(packageName: String, userId: Int, permission: AppPermission?) {
        if (permission == null) return
        runBlocking { safeRun("setAppPermission") { setAppPermission(packageName, userId, permission) } }
    }

    override fun setAppPermissions(packageName: String, userId: Int, permissions: MutableList<AppPermission>?) {
        if (permissions == null) return
        runBlocking { safeRun("setAppPermissions") { setAppPermissions(packageName, userId, permissions) } }
    }

    override fun grantRuntimePermission(packageName: String, permName: String, user: UserHandle?) {
        if (user == null) return
        runBlocking { safeRun("grantRuntimePermission") { grantRuntimePermission(packageName, permName, user) } }
    }

    override fun revokeRuntimePermission(packageName: String, permName: String, user: UserHandle?) {
        if (user == null) return
        runBlocking { safeRun("revokeRuntimePermission") { revokeRuntimePermission(packageName, permName, user) } }
    }

    override fun getPermissionFlags(packageName: String, permName: String, user: UserHandle?): Int {
        if (user == null) return 0
        return runBlocking { safeCall("getPermissionFlags", 0) { getPermissionFlags(packageName, permName, user) } }
    }

    override fun updatePermissionFlags(
        packageName: String, permName: String, user: UserHandle?, flagMask: Int, flagValues: Int
    ) {
        if (user == null) return
        runBlocking {
            safeRun("updatePermissionFlags") {
                updatePermissionFlags(packageName, permName, user, flagMask, flagValues)
            }
        }
    }

    override fun getPackageUid(packageName: String, userId: Int): Int {
        return runBlocking { safeCall("getPackageUid", -1) { getPackageUid(packageName, userId) } }
    }

    override fun getUserHandle(userId: Int): UserHandle? {
        return runBlocking { safeCall("getUserHandle", null) { getUserHandle(userId) } }
    }

    override fun setOpsMode(code: Int, uid: Int, packageName: String, mode: Int) {
        runBlocking { safeRun("setOpsMode") { setOpsMode(code, uid, packageName, mode) } }
    }

    override fun resetAppOps(userId: Int, packageName: String) {
        runBlocking { safeRun("resetAppOps") { resetAppOps(userId, packageName) } }
    }

    // ==================== SSAID ====================

    override fun getPackageSsaidAsUser(packageName: String, uid: Int, userId: Int): String? {
        return runBlocking {
            safeCall("getPackageSsaidAsUser", null) { getPackageSsaidAsUser(packageName, uid, userId) }
        }
    }

    override fun setPackageSsaidAsUser(packageName: String, userId: Int, ssaid: String) {
        runBlocking {
            val uid = safeCall("getPackageUid", -1) { getPackageUid(packageName, userId) }
            if (uid != -1) {
                safeRun("setPackageSsaidAsUser") { setPackageSsaidAsUser(packageName, uid, userId, ssaid) }
            } else {
                LogHelper.e(TAG, "setPackageSsaidAsUser", "Failed to get uid for $packageName")
            }
        }
    }

    // ==================== 应用控制 ====================

    override fun isInstalled(packageName: String, userId: Int): Boolean {
        return runBlocking { safeCall("isInstalled", false) { isInstalled(packageName, userId) } }
    }

    override fun installApk(file: String, userId: Int): Boolean {
        return runBlocking {
            Log.i(TAG, "installApk: $file")
            safeCall("installApk", false) { installApk(file, userId) }
        }
    }

    override fun installApks(files: List<String>, userId: Int): Boolean {
        if (files.isEmpty()) return false
        return runBlocking {
            Log.i(TAG, "installApks: ${files.joinToString()}")
            safeCall("installApks", false) { installApks(files, userId) }
        }
    }

    override fun uninstallApk(packageName: String, userId: Int): Boolean {
        return runBlocking { safeCall("uninstallApk", false) { uninstallApk(packageName, userId) } }
    }

    override fun forceStopPackage(packageName: String, userId: Int) {
        runBlocking { safeRun("forceStopPackage") { forceStopPackage(packageName, userId) } }
    }

    override fun clearAppData(packageName: String, userId: Int) {
        runBlocking { safeRun("clearAppData") { clearAppData(packageName, userId) } }
    }

    override fun suspendPackage(packageName: String, userId: Int) {
        runBlocking { safeRun("suspendPackage") { suspendPackage(packageName, userId) } }
    }

    override fun unsuspendPackage(packageName: String, userId: Int) {
        runBlocking { safeRun("unsuspendPackage") { unsuspendPackage(packageName, userId) } }
    }

    override fun isPackageRunning(packageName: String, userId: Int): Boolean {
        return runBlocking { safeCall("isPackageRunning", false) { isPackageRunning(packageName, userId) } }
    }

    // ==================== 启动应用 ====================

    private val intentTargetUserIdField: Field? by lazy {
        runCatching {
            Intent::class.java.getDeclaredField("mTargetUserId").also { it.isAccessible = true }
        }.getOrNull()
    }

    private fun Intent.applyFlymeUserIdCompat(userId: Int) {
        intentTargetUserIdField?.set(this, userId)
        putExtra("flyme.intent.extra.NO_MULTI_OPEN_CHOOSE", true)
    }

    override fun launchApp(packageName: String, userId: Int): Boolean {
        return try {
            val rootResult = runCatching {
                runBlocking { safeCall("launchApp", false) { launchApp(packageName, userId) } }
            }
            if (rootResult.getOrDefault(false)) return true

            LogHelper.w(TAG, "launchApp", "Root service failed for $packageName, falling back")

            val isRunning = runCatching { isPackageRunning(packageName, userId) }.getOrDefault(false)

            val userHandle = getUserHandle(userId)
            if (userHandle != null) {
                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                val activityInfoList = launcherApps.getActivityList(packageName, userHandle)
                if (activityInfoList.isNotEmpty()) {
                    val intent = Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        .setComponent(activityInfoList[0].componentName)
                    if (isRunning) intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    intent.applyFlymeUserIdCompat(userId)
                    context.startActivity(intent)
                    return true
                }
            }

            val fallbackIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (fallbackIntent != null) {
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (isRunning) fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                if (userHandle != null) fallbackIntent.applyFlymeUserIdCompat(userId)
                context.startActivity(fallbackIntent)
                true
            } else {
                LogHelper.w(TAG, "launchApp", "No launch intent for $packageName")
                false
            }
        } catch (e: Exception) {
            LogHelper.e(TAG, "launchApp", "Failed to launch $packageName", e)
            false
        }
    }

    // ==================== 工具方法 ====================

    companion object {
        private const val TAG = "AppManagerImpl"
        const val DIR_TYPE_DATA = 0
        const val DIR_TYPE_SOURCE = 1
        const val DIR_TYPE_NATIVE = 2
    }
}

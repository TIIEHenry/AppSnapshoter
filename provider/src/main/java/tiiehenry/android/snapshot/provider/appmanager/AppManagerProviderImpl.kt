package tiiehenry.android.snapshot.provider.appmanager

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.runBlocking
import tiiehenry.android.snapshot.app.AppPermission
import tiiehenry.android.snapshot.app.IAppManager
import tiiehenry.android.snapshot.provider.AppManagerProvider
import tiiehenry.android.snapshot.provider.appmanager.model.AppInfo
import tiiehenry.android.snapshot.provider.appmanager.model.AppStorage
import tiiehenry.android.snapshot.provider.appmanager.root.SnapShotRootServiceClient
import tiiehenry.android.snapshot.provider.appmanager.service.ISnapShotRootService
import tiiehenry.android.snapshot.provider.appmanager.util.LogHelper
import tiiehenry.android.snapshot.provider.appmanager.util.PathHelper
import java.lang.reflect.Field

class AppManagerProviderImpl(
    context: Context,
    val serviceClient: SnapShotRootServiceClient
) : AppManagerProvider(context) {


    override fun onInstall() {
        serviceClient.fetchRemote(context)
    }

    override fun provide(): IAppManager {
        if (serviceClient.waitFetch(context) == null) {
            throw Exception("SnapShotRootService is not available")
        }
        return AppManagerImpl(context, serviceClient)
    }

    private class AppManagerImpl(
        private val context: Context,
        private val rootServiceClient: SnapShotRootServiceClient
    ) : IAppManager.Stub() {

        private val packageManager: PackageManager = context.packageManager
        private val proxy = SnapShotRootServiceProxy(context, rootServiceClient)

        // RootService 客户端类
        private class SnapShotRootServiceProxy(
            private val context: Context,
            private val rootServiceClient: SnapShotRootServiceClient
        ) {
            val appInfos = mutableListOf<AppInfo>()

            private fun getRootService(): ISnapShotRootService {
                return try {
                    rootServiceClient.client!!
                } catch (e: Exception) {
                    LogHelper.e("RootServiceClient", "getService", "Failed to get root service", e)
                    throw e
                }
            }

            fun fetchInstalledAppInfos(): List<AppInfo> {
                val service = getRootService()
                return service.getInstalledAppInfos().let { pfd ->
                    val infos = mutableListOf<AppInfo>()
                    readFromParcel(pfd) { parcel ->
                        parcel.readTypedList(infos, AppInfo.CREATOR)
                    }
                    infos.removeIf { it.packageName == context.packageName }
                    synchronized(appInfos) {
                        appInfos.clear()
                        appInfos.addAll(infos)
                    }
                    infos
                }
            }

            suspend fun getInstalledAppInfos(): List<AppInfo> {
                if (appInfos.isEmpty()) {
                    fetchInstalledAppInfos()
                }
                return appInfos
            }

            suspend fun getInstalledAppStorages(): List<AppStorage> {
                val service = getRootService()
                return service.getInstalledAppStorages().let { pfd ->
                    val storages = mutableListOf<AppStorage>()
                    readFromParcel(pfd) { parcel ->
                        parcel.readTypedList(storages, AppStorage.CREATOR)
                    }
                    storages
                }
            }

            suspend fun getUsers(): List<android.content.pm.UserInfo> {
                val service = getRootService()
                return service.getUsers()
            }

            // 新增的应用信息获取方法
            suspend fun getPackageInfo(packageName: String, flags: Int, userId: Int): PackageInfo? {
                val service = getRootService()
                return try {
                    service.getPackageInfo(packageName, flags, userId)
                } catch (e: Exception) {
                    LogHelper.e(
                        "SnapShotRootServiceProxy",
                        "getPackageInfo",
                        "Failed to get package info",
                        e
                    )
                    null
                }
            }

            suspend fun getApplicationInfo(
                packageName: String,
                flags: Int,
                userId: Int
            ): ApplicationInfo? {
                val service = getRootService()
                return try {
                    service.getApplicationInfo(packageName, flags, userId)
                } catch (e: Exception) {
                    LogHelper.e(
                        "SnapShotRootServiceProxy",
                        "getApplicationInfo",
                        "Failed to get application info",
                        e
                    )
                    null
                }
            }

            suspend fun loadLabel(packageName: String, userId: Int): String? {
                val service = getRootService()
                return try {
                    service.loadLabel(packageName, userId)
                } catch (e: Exception) {
                    LogHelper.e("SnapShotRootServiceProxy", "loadLabel", "Failed to load label", e)
                    null
                }
            }

            suspend fun loadIcon(packageName: String, userId: Int): Bitmap? {
                val service = getRootService()
                return try {
                    service.loadIcon(packageName, userId)
                } catch (e: Exception) {
                    LogHelper.e("SnapShotRootServiceProxy", "loadIcon", "Failed to load icon", e)
                    null
                }
            }

            suspend fun getPermissions(packageName: String, userId: Int): List<AppPermission> {
                val service = getRootService()
                return try {
                    service.getPermissions(packageName, userId)
                } catch (e: Exception) {
                    LogHelper.e(
                        "SnapShotRootServiceProxy",
                        "getPermissions",
                        "Failed to get permissions",
                        e
                    )
                    emptyList()
                }
            }

            suspend fun setAppPermission(
                packageName: String,
                userId: Int,
                permission: AppPermission
            ) {
                val service = getRootService()
                try {
                    service.setAppPermission(packageName, userId, permission)
                } catch (e: Exception) {
                    LogHelper.e(
                        "SnapShotRootServiceProxy",
                        "setAppPermission",
                        "Failed to set permission",
                        e
                    )
                }
            }

            suspend fun setAppPermissions(
                packageName: String,
                userId: Int,
                permissions: List<AppPermission>
            ) {
                val service = getRootService()
                try {
                    service.setAppPermissions(packageName, userId, permissions)
                } catch (e: Exception) {
                    LogHelper.e(
                        "SnapShotRootServiceProxy",
                        "setAppPermissions",
                        "Failed to set permissions",
                        e
                    )
                }
            }

            suspend fun isInstalled(packageName: String, userId: Int): Boolean {
                val service = getRootService()
                return try {
                    service.isInstalled(packageName, userId)
                } catch (e: Exception) {
                    LogHelper.e(
                        "SnapShotRootServiceProxy",
                        "isInstalled",
                        "Failed to check installation",
                        e
                    )
                    false
                }
            }

            suspend fun installApk(file: String, userId: Int): Boolean {
                val service = getRootService()
                return try {
                    Log.i("SnapShotRootServiceProxy", "installApk: $file")
                    service.installApk(file, userId)
                } catch (e: Exception) {
                    LogHelper.e(
                        "SnapShotRootServiceProxy",
                        "installApk",
                        "Failed to install APK",
                        e
                    )
                    false
                }
            }

            suspend fun installApks(files: List<String>, userId: Int): Boolean {
                val service = getRootService()
                return try {
                    Log.i("SnapShotRootServiceProxy", "installApks: ${files.joinToString()}")
                    service.installApks(files, userId)
                } catch (e: Exception) {
                    LogHelper.e(
                        "SnapShotRootServiceProxy",
                        "installApks",
                        "Failed to install APKs",
                        e
                    )
                    false
                }
            }

            suspend fun uninstallApk(packageName: String, userId: Int): Boolean {
                val service = getRootService()
                return try {
                    service.uninstallApk(packageName, userId)
                } catch (e: Exception) {
                    LogHelper.e(
                        "SnapShotRootServiceProxy",
                        "uninstallApk",
                        "Failed to uninstall APK",
                        e
                    )
                    false
                }
            }

            suspend fun forceStopPackage(packageName: String, userId: Int) {
                val service = getRootService()
                try {
                    service.forceStopPackage(packageName, userId)
                } catch (e: Exception) {
                    LogHelper.e(
                        "SnapShotRootServiceProxy",
                        "forceStopPackage",
                        "Failed to force-stop package",
                        e
                    )
                }
            }

            suspend fun clearAppData(packageName: String, userId: Int) {
                val service = getRootService()
                try {
                    service.clearAppData(packageName, userId)
                } catch (e: Exception) {
                    LogHelper.e(
                        "SnapShotRootServiceProxy",
                        "clearAppData",
                        "Failed to clear app data",
                        e
                    )
                }
            }

            suspend fun suspendPackage(packageName: String, userId: Int) {
                val service = getRootService()
                try {
                    service.suspendPackage(packageName, userId)
                } catch (e: Exception) {
                    LogHelper.e(
                        "SnapShotRootServiceProxy",
                        "suspendPackage",
                        "Failed to suspend package",
                        e
                    )
                }
            }

            suspend fun unsuspendPackage(packageName: String, userId: Int) {
                val service = getRootService()
                try {
                    service.unsuspendPackage(packageName, userId)
                } catch (e: Exception) {
                    LogHelper.e(
                        "SnapShotRootServiceProxy",
                        "unsuspendPackage",
                        "Failed to unsuspend package",
                        e
                    )
                }
            }

            suspend fun grantRuntimePermission(
                packageName: String,
                permName: String,
                user: android.os.UserHandle
            ) {
                val service = getRootService()
                service.grantRuntimePermission(packageName, permName, user)
            }

            suspend fun revokeRuntimePermission(
                packageName: String,
                permName: String,
                user: android.os.UserHandle
            ) {
                val service = getRootService()
                service.revokeRuntimePermission(packageName, permName, user)
            }

            suspend fun getPermissionFlags(
                packageName: String,
                permName: String,
                user: android.os.UserHandle
            ): Int {
                val service = getRootService()
                return try {
                    service.getPermissionFlags(packageName, permName, user)
                } catch (e: Exception) {
                    LogHelper.e(
                        "SnapShotRootServiceProxy",
                        "getPermissionFlags",
                        "Failed to get permission flags",
                        e
                    )
                    0
                }
            }

            suspend fun updatePermissionFlags(
                packageName: String,
                permName: String,
                user: android.os.UserHandle,
                flagMask: Int,
                flagValues: Int
            ) {
                val service = getRootService()
                try {
                    service.updatePermissionFlags(packageName, permName, user, flagMask, flagValues)
                } catch (e: Exception) {
                    LogHelper.e(
                        "SnapShotRootServiceProxy",
                        "updatePermissionFlags",
                        "Failed to update permission flags",
                        e
                    )
                }
            }

            suspend fun getPackageUid(packageName: String, userId: Int): Int {
                val service = getRootService()
                return try {
                    service.getPackageUid(packageName, userId)
                } catch (e: Exception) {
                    LogHelper.e(
                        "SnapShotRootServiceProxy",
                        "getPackageUid",
                        "Failed to get package uid",
                        e
                    )
                    -1
                }
            }

            suspend fun getUserHandle(userId: Int): android.os.UserHandle? {
                val service = getRootService()
                return try {
                    service.getUserHandle(userId)
                } catch (e: Exception) {
                    LogHelper.e(
                        "SnapShotRootServiceProxy",
                        "getUserHandle",
                        "Failed to get user handle",
                        e
                    )
                    null
                }
            }

            suspend fun setOpsMode(code: Int, uid: Int, packageName: String, mode: Int) {
                val service = getRootService()
                try {
                    service.setOpsMode(code, uid, packageName, mode)
                } catch (e: Exception) {
                    LogHelper.e(
                        "SnapShotRootServiceProxy",
                        "setOpsMode",
                        "Failed to set ops mode",
                        e
                    )
                }
            }

            suspend fun resetAppOps(userId: Int, packageName: String) {
                val service = getRootService()
                try {
                    service.resetAppOps(userId, packageName)
                } catch (e: Exception) {
                    LogHelper.e(
                        "SnapShotRootServiceProxy",
                        "resetAppOps",
                        "Failed to reset appops",
                        e
                    )
                }
            }

            suspend fun getPackageSsaidAsUser(packageName: String, uid: Int, userId: Int): String? {
                val service = getRootService()
                return try {
                    service.getPackageSsaidAsUser(packageName, uid, userId)
                } catch (e: Exception) {
                    LogHelper.e(
                        "SnapShotRootServiceProxy",
                        "getPackageSsaidAsUser",
                        "Failed to get ssaid",
                        e
                    )
                    null
                }
            }

            suspend fun setPackageSsaidAsUser(packageName: String, userId: Int, ssaid: String) {
                val service = getRootService()
                try {
                    val uid = getPackageUid(packageName, userId)
                    if (uid != -1) {
                        service.setPackageSsaidAsUser(packageName, uid, userId, ssaid)
                    } else {
                        LogHelper.e(
                            "SnapShotRootServiceProxy",
                            "setPackageSsaidAsUser",
                            "Failed to get uid for package $packageName",
                            Exception()
                        )
                    }
                } catch (e: Exception) {
                    LogHelper.e(
                        "SnapShotRootServiceProxy",
                        "setPackageSsaidAsUser",
                        "Failed to set ssaid",
                        e
                    )
                }
            }

            suspend fun isPackageRunning(packageName: String, userId: Int): Boolean {
                val service = getRootService()
                return try {
                    service.isPackageRunning(packageName, userId)
                } catch (e: Exception) {
                    LogHelper.e(
                        "SnapShotRootServiceProxy",
                        "isPackageRunning",
                        "Failed to check if package is running",
                        e
                    )
                    false
                }
            }

            suspend fun launchApp(packageName: String, userId: Int): Boolean {
                val service = getRootService()
                return try {
                    service.launchApp(packageName, userId)
                } catch (e: Exception) {
                    LogHelper.e(
                        "SnapShotRootServiceProxy",
                        "launchApp",
                        "Failed to launch app via root service",
                        e
                    )
                    false
                }
            }

            private fun writeToParcel(
                context: Context,
                block: (Parcel) -> Unit
            ): ParcelFileDescriptor {
                val parcel = Parcel.obtain()
                parcel.setDataPosition(0)
                block(parcel)
                val tmpFile = java.io.File.createTempFile(
                    PathHelper.TMP_PARCEL_PREFIX,
                    PathHelper.TMP_SUFFIX,
                    context.cacheDir
                )
                tmpFile.delete()
                tmpFile.createNewFile()
                tmpFile.writeBytes(parcel.marshall())
                val pfd = ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_WRITE)
                tmpFile.delete()
                parcel.recycle()
                return pfd
            }

            private fun readFromParcel(pfd: ParcelFileDescriptor, block: (Parcel) -> Unit) {
                val stream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
                val bytes = stream.readBytes()
                val parcel = Parcel.obtain()
                parcel.unmarshall(bytes, 0, bytes.size)
                parcel.setDataPosition(0)
                block(parcel)
                parcel.recycle()
            }
        }

        //原方法\u5b9e\u73b0\.\.

        override fun getUsers(): List<tiiehenry.android.snapshot.app.UserInfoParcelable> {
            return runBlocking {
                proxy.getUsers().map { userInfo ->
                    tiiehenry.android.snapshot.app.UserInfoParcelable(
                        userInfo.id,
                        userInfo.name ?: ""
                    )
                }
            }
        }

        override fun getInstalledPackages(flags: Int, userId: Int): List<String> {
            // 使用 RootService 获取系统级应用信息
            return runBlocking {
                proxy.fetchInstalledAppInfos()
                proxy.getInstalledAppInfos().map { it.packageName }
            }
        }

        override fun getPackageInfo(
            packageName: String,
            flags: Int,
            userId: Int
        ): PackageInfo? {
            return try {
                // 使用 RootService 获取系统级包信息
                runBlocking {
                    proxy.getPackageInfo(packageName, flags, userId)
                }
            } catch (e: Exception) {
                // 降级到\u666e\u901aPackageManager
                LogHelper.w(
                    "AppManagerImpl",
                    "getPackageInfo",
                    "Failed to use root service, falling back to PackageManager",
                    e
                )
                packageManager.getPackageInfo(packageName, flags)
            }
        }

        override fun getApplicationInfo(
            packageName: String,
            flags: Int,
            userId: Int
        ): ApplicationInfo? {
            // 使用 RootService 获取系统级应用信息
            return runBlocking {
                proxy.getApplicationInfo(packageName, flags, userId)
            }
        }

        override fun loadLabel(packageName: String, userId: Int): String? {
            // 使用 RootService 获取应用标签
            return runBlocking {
                proxy.loadLabel(packageName, userId)
            }
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
//            return try {
//                // 使用 RootService 获取应用图标
//                runBlocking {
//                    proxy.loadIcon(packageName, userId)
//                }
//            } catch (e: Throwable) {
//                LogHelper.w(
//                    "AppManagerImpl",
//                    "loadIcon",
//                    "Failed to use root service, falling back to PackageManager",
//                    e
//                )
//                try {
//                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
//                    val drawable = packageManager.getApplicationIcon(appInfo)
//                    drawableToBitmap(drawable)
//                } catch (ex: Exception) {
//                    ex.printStackTrace()
//                    null
//                }
//            }
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
                LogHelper.e("AppManagerImpl", "getDir", "Failed to get directory", e)
                null
            }
        }

        override fun getPermissions(
            packageName: String,
            userId: Int
        ): List<AppPermission?>? {
            // 使用 RootService 获取权限信息
            return runBlocking {
                proxy.getPermissions(packageName, userId)
            }
        }

        override fun setAppPermission(
            packageName: String,
            userId: Int,
            permission: AppPermission?
        ) {
            if (permission == null) return
            // 使用 RootService 设置权限
            runBlocking {
                proxy.setAppPermission(packageName, userId, permission)
            }
        }

        override fun setAppPermissions(
            packageName: String,
            userId: Int,
            permissions: MutableList<AppPermission>?
        ) {
            if (permissions == null) return
            // 使用 RootService 批量设置权限
            runBlocking {
                proxy.setAppPermissions(packageName, userId, permissions)
            }
        }

        private fun isDeviceOwnerOrProfileOwner(dpm: DevicePolicyManager): Boolean {
            return try {
                dpm.isDeviceOwnerApp(context.packageName) || dpm.isProfileOwnerApp(context.packageName)
            } catch (e: Exception) {
                false
            }
        }

        override fun isInstalled(packageName: String, userId: Int): Boolean {
            // 使用 RootService 检查应用是否安装
            return runBlocking {
                proxy.isInstalled(packageName, userId)
            }
        }

        override fun installApk(file: String, userId: Int): Boolean {
            // 使用 RootService 安装 APK
            return runBlocking {
                proxy.installApk(file, userId)
            }
        }

        override fun installApks(files: List<String>, userId: Int): Boolean {
            if (files.isEmpty()) return false
            // 使用 RootService 安装多个 APK
            return runBlocking {
                proxy.installApks(files, userId)
            }
        }

        override fun uninstallApk(packageName: String, userId: Int): Boolean {
            // 使用 RootService 卸载 APK
            return runBlocking {
                proxy.uninstallApk(packageName, userId)
            }
        }

        override fun forceStopPackage(packageName: String, userId: Int) {
            runBlocking {
                proxy.forceStopPackage(packageName, userId)
            }
        }

        override fun clearAppData(packageName: String, userId: Int) {
            runBlocking {
                proxy.clearAppData(packageName, userId)
            }
        }

        override fun suspendPackage(packageName: String, userId: Int) {
            runBlocking {
                proxy.suspendPackage(packageName, userId)
            }
        }

        override fun unsuspendPackage(packageName: String, userId: Int) {
            runBlocking {
                proxy.unsuspendPackage(packageName, userId)
            }
        }

        override fun grantRuntimePermission(
            packageName: String,
            permName: String,
            user: android.os.UserHandle?
        ) {
            if (user == null) return
            runBlocking {
                proxy.grantRuntimePermission(packageName, permName, user)
            }
        }

        override fun revokeRuntimePermission(
            packageName: String,
            permName: String,
            user: android.os.UserHandle?
        ) {
            if (user == null) return
            runBlocking {
                proxy.revokeRuntimePermission(packageName, permName, user)
            }
        }

        override fun getPermissionFlags(
            packageName: String,
            permName: String,
            user: android.os.UserHandle?
        ): Int {
            if (user == null) return 0
            return runBlocking {
                proxy.getPermissionFlags(packageName, permName, user)
            }
        }

        override fun updatePermissionFlags(
            packageName: String,
            permName: String,
            user: android.os.UserHandle?,
            flagMask: Int,
            flagValues: Int
        ) {
            if (user == null) return
            runBlocking {
                proxy.updatePermissionFlags(packageName, permName, user, flagMask, flagValues)
            }
        }

        override fun getPackageUid(packageName: String, userId: Int): Int {
            return runBlocking {
                proxy.getPackageUid(packageName, userId)
            }
        }

        override fun getUserHandle(userId: Int): android.os.UserHandle? {
            return runBlocking {
                proxy.getUserHandle(userId)
            }
        }

        override fun setOpsMode(code: Int, uid: Int, packageName: String, mode: Int) {
            runBlocking {
                proxy.setOpsMode(code, uid, packageName, mode)
            }
        }

        override fun resetAppOps(userId: Int, packageName: String) {
            runBlocking {
                proxy.resetAppOps(userId, packageName)
            }
        }

        override fun getPackageSsaidAsUser(packageName: String, uid: Int, userId: Int): String? {
            return runBlocking {
                proxy.getPackageSsaidAsUser(packageName, uid, userId)
            }
        }

        override fun setPackageSsaidAsUser(packageName: String, userId: Int, ssaid: String) {
            runBlocking {
                proxy.setPackageSsaidAsUser(packageName, userId, ssaid)
            }
        }

        override fun isPackageRunning(packageName: String, userId: Int): Boolean {
            return runBlocking {
                proxy.isPackageRunning(packageName, userId)
            }
        }

        /**
         * Intent.mTargetUserId 字段缓存（Flyme 多用户适配）。
         * 首次访问时尝试获取，字段不存在的设备上为 null。
         */
        val intentTargetUserIdField: Field? by lazy {
            runCatching {
                Intent::class.java.getDeclaredField("mTargetUserId").also { it.isAccessible = true }
            }.getOrNull()
        }

        /**
         * Flyme 多用户适配：通过 ReflectionCache 将 userId 写入 intent.mTargetUserId，
         * 并附加 Flyme 专属 Extra，字段不存在时静默忽略。
         */
        private fun Intent.applyFlymeUserIdCompat(userId: Int) {
            intentTargetUserIdField?.set(this, userId)
            putExtra("flyme.intent.extra.NO_MULTI_OPEN_CHOOSE", true)
        }

        override fun launchApp(packageName: String, userId: Int): Boolean {
            return try {
                // 如果应用已在后台运行， Root Service 会内部处理 moveTaskToFront
                // 内部 am moveTaskToFront 失败或未运行时，尝试优先使用 Root Service
                val rootResult = runCatching {
                    runBlocking { proxy.launchApp(packageName, userId) }
                }
                if (rootResult.getOrDefault(false)) {
                    return true
                }
                LogHelper.w(
                    "AppManagerImpl",
                    "launchApp",
                    "Root service launch failed for $packageName (userId=$userId), falling back to LauncherApps"
                )

                // 检测应用是否已在运行，若已在后台运行则加入 FLAG_ACTIVITY_REORDER_TO_FRONT
                // 让系统将已有 Task 移至前台，而不是创建新的 Task
                val isRunning =
                    runCatching { isPackageRunning(packageName, userId) }.getOrDefault(false)

                // 降级：使用 LauncherApps （支持多用户）
                val userHandle = getUserHandle(userId)
                if (userHandle != null) {
                    val launcherApps =
                        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                    val activityInfoList = launcherApps.getActivityList(packageName, userHandle)
                    if (activityInfoList.isNotEmpty()) {
                        val intent = Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_LAUNCHER)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                            .setComponent(activityInfoList[0].componentName)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (isRunning) {
                            // 应用已在运行，将现有 Task 移至前台而不创建新 Task
                            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        }
                        intent.applyFlymeUserIdCompat(userId)
                        context.startActivity(intent)
                        true
                    } else {
                        // 降级到普通方式
                        val fallbackIntent = packageManager.getLaunchIntentForPackage(packageName)
                        if (fallbackIntent != null) {
                            fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            if (isRunning) {
                                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                            }
                            context.startActivity(fallbackIntent)
                            true
                        } else {
                            LogHelper.w(
                                "AppManagerImpl",
                                "launchApp",
                                "No launchable activity found for $packageName"
                            )
                            false
                        }
                    }
                } else {
                    // 回退到默认方式启动
                    val fallbackIntent = packageManager.getLaunchIntentForPackage(packageName)
                    if (fallbackIntent != null) {
                        fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (isRunning) {
                            fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        }
                        fallbackIntent.applyFlymeUserIdCompat(userId)
                        context.startActivity(fallbackIntent)
                        true
                    } else {
                        LogHelper.w(
                            "AppManagerImpl",
                            "launchApp",
                            "No launch intent found for $packageName"
                        )
                        false
                    }
                }
            } catch (e: Exception) {
                LogHelper.e("AppManagerImpl", "launchApp", "Failed to launch app: $packageName", e)
                false
            }
        }

        private fun drawableToBitmap(drawable: Drawable): Bitmap {
            if (drawable is BitmapDrawable) {
                if (drawable.bitmap != null) {
                    return drawable.bitmap
                }
            }

            val bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
                createBitmap(1, 1)
            } else {
                createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
            }

            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        }

        companion object {
            const val DIR_TYPE_DATA = 0
            const val DIR_TYPE_SOURCE = 1
            const val DIR_TYPE_NATIVE = 2
        }
    }
}
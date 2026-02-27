package tiiehenry.android.snapshotor.provider.appmanager

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.runBlocking
import tiiehenry.android.snapshotor.app.AppPermission
import tiiehenry.android.snapshotor.app.IAppManager
import tiiehenry.android.snapshotor.provider.AppManagerProvider
import tiiehenry.android.snapshotor.provider.appmanager.model.AppInfo
import tiiehenry.android.snapshotor.provider.appmanager.model.AppStorage
import tiiehenry.android.snapshotor.provider.appmanager.service.AppManageRootServiceClient
import tiiehenry.android.snapshotor.provider.appmanager.service.IAppManageRootService
import tiiehenry.android.snapshotor.provider.appmanager.util.LogHelper
import tiiehenry.android.snapshotor.provider.appmanager.util.PathHelper

class AppManagerProviderImpl(
    hostContext: Context,
    pluginContext: Context
) : AppManagerProvider(hostContext, pluginContext) {

    val serviceClient = AppManageRootServiceClient.getInstance()

    override fun onInstall() {
        serviceClient.fetchRemote(pluginContext)
    }

    override fun provide(): IAppManager {
        if (serviceClient.waitFetch(pluginContext) == null) {
            throw Exception("AppManageRootService is not available")
        }
        return AppManagerImpl(hostContext, pluginContext, serviceClient)
    }

    private class AppManagerImpl(
        private val hostContext: Context,
        private val pluginContext: Context,
        private val rootServiceClient: AppManageRootServiceClient
    ) : IAppManager.Stub() {

        private val packageManager: PackageManager = hostContext.packageManager
        private val proxy = AppManageRootServiceProxy(hostContext, rootServiceClient)

        // RootService 客户端类
        private class AppManageRootServiceProxy(
            private val context: Context,
            private val rootServiceClient: AppManageRootServiceClient
        ) {
            val appInfos = mutableListOf<AppInfo>()

            private fun getRootService(): IAppManageRootService {
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
                        "AppManageRootServiceProxy",
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
                        "AppManageRootServiceProxy",
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
                    LogHelper.e("AppManageRootServiceProxy", "loadLabel", "Failed to load label", e)
                    null
                }
            }

            suspend fun loadIcon(packageName: String, userId: Int): Bitmap? {
                val service = getRootService()
                return try {
                    service.loadIcon(packageName, userId)
                } catch (e: Exception) {
                    LogHelper.e("AppManageRootServiceProxy", "loadIcon", "Failed to load icon", e)
                    null
                }
            }

            suspend fun getPermissions(packageName: String, userId: Int): List<AppPermission> {
                val service = getRootService()
                return try {
                    service.getPermissions(packageName, userId)
                } catch (e: Exception) {
                    LogHelper.e(
                        "AppManageRootServiceProxy",
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
                        "AppManageRootServiceProxy",
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
                        "AppManageRootServiceProxy",
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
                        "AppManageRootServiceProxy",
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
                    service.installApk(file, userId)
                } catch (e: Exception) {
                    LogHelper.e(
                        "AppManageRootServiceProxy",
                        "installApk",
                        "Failed to install APK",
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
                        "AppManageRootServiceProxy",
                        "uninstallApk",
                        "Failed to uninstall APK",
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

        //原方法实现...

        override fun getInstalledPackages(flags: Int, userId: Int): List<String> {
            return try {
                // 使用RootService获取系统级应用信息
                runBlocking {
                    proxy.fetchInstalledAppInfos()
                    proxy.getInstalledAppInfos().map { it.packageName }
                }
            } catch (e: Exception) {
                // 降级到普通PackageManager
                LogHelper.w(
                    "AppManagerImpl",
                    "getInstalledPackages",
                    "Failed to use root service, falling back to PackageManager",
                    e
                )
                packageManager.getInstalledPackages(flags).map { it.packageName }
            }
        }

        override fun getPackageInfo(
            packageName: String?,
            flags: Int,
            userId: Int
        ): PackageInfo? {
            if (packageName == null) return null
            return try {
                // 使用 RootService 获取系统级包信息
                runBlocking {
                    proxy.getPackageInfo(packageName, flags, userId)
                }
            } catch (e: Exception) {
                // 降级到普通 PackageManager
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
            packageName: String?,
            flags: Int,
            userId: Int
        ): ApplicationInfo? {
            if (packageName == null) return null
            return try {
                // 使用 RootService 获取系统级应用信息
                runBlocking {
                    proxy.getApplicationInfo(packageName, flags, userId)
                }
            } catch (e: Exception) {
                // 降级到普通 PackageManager
                LogHelper.w(
                    "AppManagerImpl",
                    "getApplicationInfo",
                    "Failed to use root service, falling back to PackageManager",
                    e
                )
                packageManager.getApplicationInfo(packageName, flags)
            }
        }

        override fun loadLabel(packageName: String?, userId: Int): String? {
            if (packageName == null) return null
            return try {
                // 使用 RootService 获取应用标签
                runBlocking {
                    proxy.loadLabel(packageName, userId)
                }
            } catch (e: Exception) {
                // 降级到普通 PackageManager
                LogHelper.w(
                    "AppManagerImpl",
                    "loadLabel",
                    "Failed to use root service, falling back to PackageManager",
                    e
                )
                try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (ex: Exception) {
                    packageName
                }
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

        override fun getDir(packageName: String?, userId: Int, type: Int): String? {
            if (packageName == null) return null
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
            packageName: String?,
            userId: Int
        ): List<AppPermission?>? {
            if (packageName == null) return null
            return try {
                // 使用 RootService 获取权限信息
                runBlocking {
                    proxy.getPermissions(packageName, userId)
                }
            } catch (e: Exception) {
                LogHelper.e("AppManagerImpl", "getPermissions", "Failed to get permissions", e)
                null
            }
        }

        override fun setAppPermission(
            packageName: String?,
            userId: Int,
            permission: AppPermission?
        ) {
            if (packageName == null || permission == null) return
            try {
                // 使用 RootService 设置权限
                runBlocking {
                    proxy.setAppPermission(packageName, userId, permission)
                }
            } catch (e: Exception) {
                LogHelper.e("AppManagerImpl", "setAppPermission", "Failed to set permission", e)
            }
        }

        override fun setAppPermissions(
            packageName: String?,
            userId: Int,
            permissions: MutableList<AppPermission>?
        ) {
            if (packageName == null || permissions == null) return
            // 使用 RootService 批量设置权限
            runBlocking {
                proxy.setAppPermissions(packageName, userId, permissions)
            }
        }

        private fun isDeviceOwnerOrProfileOwner(dpm: DevicePolicyManager): Boolean {
            return try {
                dpm.isDeviceOwnerApp(hostContext.packageName) || dpm.isProfileOwnerApp(hostContext.packageName)
            } catch (e: Exception) {
                false
            }
        }

        override fun isInstalled(packageName: String?, userId: Int): Boolean {
            if (packageName == null) return false
            return try {
                // 使用 RootService 检查应用是否安装
                runBlocking {
                    proxy.isInstalled(packageName, userId)
                }
            } catch (e: Exception) {
                // 降级到普通 PackageManager
                LogHelper.w(
                    "AppManagerImpl",
                    "isInstalled",
                    "Failed to use root service, falling back to PackageManager",
                    e
                )
                try {
                    packageManager.getPackageInfo(packageName, 0)
                    true
                } catch (ex: Exception) {
                    false
                }
            }
        }

        override fun installApk(file: String?, userId: Int): Boolean {
            if (file == null) return false
            return try {
                // 使用 RootService 安装 APK
                runBlocking {
                    proxy.installApk(file, userId)
                }
            } catch (e: Exception) {
                LogHelper.e("AppManagerImpl", "installApk", "Failed to install APK", e)
                false
            }
        }

        override fun uninstallApk(packageName: String?, userId: Int): Boolean {
            if (packageName == null) return false
            return try {
                // 使用 RootService 卸载 APK
                runBlocking {
                    proxy.uninstallApk(packageName, userId)
                }
            } catch (e: Exception) {
                LogHelper.e("AppManagerImpl", "uninstallApk", "Failed to uninstall APK", e)
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
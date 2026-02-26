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
import android.os.*
import kotlinx.coroutines.*
import tiiehenry.android.snapshotor.app.AppPermission
import tiiehenry.android.snapshotor.provider.AppManagerProvider
import tiiehenry.android.snapshotor.app.IAppManager
import androidx.core.graphics.createBitmap
import tiiehenry.android.snapshotor.provider.appmanager.model.*
import tiiehenry.android.snapshotor.provider.appmanager.parcelables.*
import tiiehenry.android.snapshotor.provider.appmanager.service.*
import tiiehenry.android.snapshotor.provider.appmanager.service.AppManageRootServiceClient
import tiiehenry.android.snapshotor.provider.appmanager.util.*

class AppManagerProviderImpl(
    hostContext: Context,
    pluginContext: Context
) : AppManagerProvider(hostContext, pluginContext) {

    override fun provide(): IAppManager {
        return AppManagerImpl(hostContext, pluginContext)
    }

    private class AppManagerImpl(
        private val hostContext: Context,
        private val pluginContext: Context
    ) : IAppManager.Stub() {

        private val packageManager: PackageManager = hostContext.packageManager
        private val rootServiceClient = RootServiceClient(hostContext)
        
        // RootService客户端类
        private class RootServiceClient(private val context: Context) {
            
        private suspend fun getRootService(): IAppManageRootService {
            return try {
                AppManageRootServiceClient.getInstance().fetchRemote(context)!!
            } catch (e: Exception) {
                LogHelper.e("RootServiceClient", "getService", "Failed to get root service", e)
                throw e
            }
        }
            
            suspend fun getInstalledAppInfos(): List<AppInfo> {
                val service = getRootService()
                return service.getInstalledAppInfos().let { pfd ->
                    val infos = mutableListOf<AppInfo>()
                    readFromParcel(pfd) { parcel ->
                        parcel.readTypedList(infos, AppInfo.CREATOR)
                    }
                    infos
                }
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
            
            suspend fun readStatFs(path: String): StatFsParcelable? {
                val service = getRootService()
                return service.readStatFs(path)
            }
            
            suspend fun listFilePaths(path: String, listFiles: Boolean, listDirs: Boolean): List<FilePathParcelable> {
                val service = getRootService()
                return service.listFilePaths(path, listFiles, listDirs)
            }
            
            suspend fun readText(path: String): String {
                val service = getRootService()
                var text = ""
                service.readText(path).let { pfd ->
                    readFromParcel(pfd) { parcel ->
                        parcel.readString()?.also { text = it }
                    }
                }
                return text
            }
            
            suspend fun writeText(path: String, text: String) {
                val service = getRootService()
                service.writeText(
                    path,
                    writeToParcel(context) { parcel ->
                        parcel.writeString(text)
                    }
                )
            }
            
            suspend fun calculateTreeSize(path: String): Long {
                val service = getRootService()
                return service.calculateTreeSize(path)
            }
            
            suspend fun mkdirs(path: String): Boolean {
                val service = getRootService()
                return service.mkdirs(path)
            }
            
            suspend fun exists(path: String): Boolean {
                val service = getRootService()
                return service.exists(path)
            }
            
            suspend fun deleteRecursively(path: String): Boolean {
                val service = getRootService()
                return service.deleteRecursively(path)
            }
            
            suspend fun copyRecursively(source: String, target: String, overwrite: Boolean = false): Boolean {
                val service = getRootService()
                return service.copyRecursively(source, target, overwrite)
            }
            
            private fun writeToParcel(context: Context, block: (Parcel) -> Unit): ParcelFileDescriptor {
                val parcel = Parcel.obtain()
                parcel.setDataPosition(0)
                block(parcel)
                val tmpFile = java.io.File.createTempFile(PathHelper.TMP_PARCEL_PREFIX, PathHelper.TMP_SUFFIX, context.cacheDir)
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
                    rootServiceClient.getInstalledAppInfos().map { it.packageName }
                }
            } catch (e: Exception) {
                // 降级到普通PackageManager
                LogHelper.w("AppManagerImpl", "getInstalledPackages", "Failed to use root service, falling back to PackageManager", e)
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
                // 使用RootService获取系统级包信息
                runBlocking {
                    val appInfos = rootServiceClient.getInstalledAppInfos()
                    val appInfo = appInfos.find { it.packageName == packageName && it.userId == userId }
                    if (appInfo != null) {
                        //构造PackageInfo对象（简化实现）
                        PackageInfo().apply {
                            this.packageName = appInfo.packageName
                            this.applicationInfo = ApplicationInfo().apply {
                                this.uid = appInfo.info.uid
                                this.flags = appInfo.info.flags
                            }
                            this.versionName = appInfo.info.versionName
                            this.versionCode = appInfo.info.versionCode.toInt()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                this.longVersionCode = appInfo.info.versionCode
                            }
                            this.firstInstallTime = appInfo.info.firstInstallTime
                            this.lastUpdateTime = appInfo.info.lastUpdateTime
                        }
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                // 降级到普通PackageManager
                LogHelper.w("AppManagerImpl", "getPackageInfo", "Failed to use root service, falling back to PackageManager", e)
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
                // 使用RootService获取系统级应用信息
                runBlocking {
                    val appInfos = rootServiceClient.getInstalledAppInfos()
                    val appInfo = appInfos.find { it.packageName == packageName && it.userId == userId }
                    if (appInfo != null) {
                        ApplicationInfo().apply {
                            this.uid = appInfo.info.uid
                            this.flags = appInfo.info.flags
                            // 设置基本路径信息
                            this.packageName = appInfo.packageName
                            this.sourceDir = PathHelper.getAppUserDir(userId, packageName)
                            this.dataDir = PathHelper.getAppDataDir(userId, packageName)
                        }
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                // 降级到普通PackageManager
                LogHelper.w("AppManagerImpl", "getApplicationInfo", "Failed to use root service, falling back to PackageManager", e)
                packageManager.getApplicationInfo(packageName, flags)
            }
        }

        override fun loadLabel(packageName: String?, userId: Int): String? {
            if (packageName == null) return null
            return try {
                // 使用RootService获取应用标签
                runBlocking {
                    val appInfos = rootServiceClient.getInstalledAppInfos()
                    val appInfo = appInfos.find { it.packageName == packageName && it.userId == userId }
                    appInfo?.info?.label ?: packageName
                }
            } catch (e: Exception) {
                // 降级到普通PackageManager
                LogHelper.w("AppManagerImpl", "loadLabel", "Failed to use root service, falling back to PackageManager", e)
                try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (ex: Exception) {
                    packageName
                }
            }
        }

        override fun loadIcon(packageName: String?, userId: Int): Bitmap? {
            if (packageName == null) return null
            return try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val drawable = packageManager.getApplicationIcon(appInfo)
                drawableToBitmap(drawable)
            } catch (e: Exception) {
                null
            }
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
            TODO("Not yet implemented")
        }

        override fun setAppPermission(
            packageName: String?,
            userId: Int,
            permission: AppPermission?
        ) {
            if (packageName == null || permission == null) return

            try {
                val dpm =
                    hostContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager?


                // Check if we have device admin rights
                if (dpm != null && isDeviceOwnerOrProfileOwner(dpm)) {
                    // Use DevicePolicyManager to set permission grant state
                    val grantState = if (permission.isGranted) {
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                    } else {
                        DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
                    }

                    // Set permission grant state for the specified package
                    dpm.setPermissionGrantState(
                        null,
                        packageName,
                        permission.name,
                        grantState
                    )
                } else {
                    // For non-device-owner apps, we can only request permissions for our own app
                    // This is a limitation of Android's security model
                    // We log the intended action but cannot actually set permissions for other apps
                    android.util.Log.w(
                        "AppManagerImpl",
                        "Cannot set permissions for other apps without device admin rights. " +
                                "PackageName: $packageName, Permission: ${permission.name}"
                    )
                }
            } catch (e: SecurityException) {
                // Catch security exceptions when lacking required permissions
                android.util.Log.e(
                    "AppManagerImpl",
                    "Security exception when setting permission: ${e.message}",
                    e
                )
            } catch (e: Exception) {
                // Log error but don't crash the service
                android.util.Log.e(
                    "AppManagerImpl",
                    "Error setting permission: ${e.message}",
                    e
                )
            }
        }

        override fun setAppPermissions(
            packageName: String?,
            userId: Int,
            permissions: MutableList<AppPermission>?
        ) {
            if (packageName == null || permissions == null) return

            // Apply each permission individually
            for (permission in permissions) {
                setAppPermission(packageName, userId, permission)
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
                // 使用RootService检查应用是否安装
                runBlocking {
                    val appInfos = rootServiceClient.getInstalledAppInfos()
                    appInfos.any { it.packageName == packageName && it.userId == userId }
                }
            } catch (e: Exception) {
                // 降级到普通PackageManager
                LogHelper.w("AppManagerImpl", "isInstalled", "Failed to use root service, falling back to PackageManager", e)
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
            // TODO: Implement APK installation logic
            // This typically requires system permissions or PackageInstaller API
            return false
        }

        override fun uninstallApk(packageName: String?, userId: Int): Boolean {
            if (packageName == null) return false
            // TODO: Implement APK uninstallation logic
            // This typically requires system permissions or PackageInstaller API
            return false
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
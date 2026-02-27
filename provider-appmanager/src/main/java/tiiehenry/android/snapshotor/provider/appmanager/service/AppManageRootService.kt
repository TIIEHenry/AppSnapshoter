package tiiehenry.android.snapshotor.provider.appmanager.service

import android.app.ActivityThread
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManagerHidden
import android.content.pm.UserInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.UserManagerHidden
import android.util.Log
import com.topjohnwu.superuser.ipc.RootService
import com.xayah.hiddenapi.castTo
import com.xayah.libnative.NativeLib
import tiiehenry.android.snapshotor.app.AppPermission
import tiiehenry.android.snapshotor.provider.appmanager.model.AppInfo
import tiiehenry.android.snapshotor.provider.appmanager.model.AppStorage
import tiiehenry.android.snapshotor.provider.appmanager.model.Info
import tiiehenry.android.snapshotor.provider.appmanager.model.Storage
import tiiehenry.android.snapshotor.provider.appmanager.parcelables.BytesParcelable
import tiiehenry.android.snapshotor.provider.appmanager.util.LogHelper
import tiiehenry.android.snapshotor.provider.appmanager.util.PathHelper
import java.io.File

class AppManageRootService : RootService() {
    override fun onBind(intent: Intent): IBinder = Impl(applicationContext).apply { onBind() }

    private class Impl(private val context: Context) : IAppManageRootService.Stub() {
        private lateinit var mSystemContext: Context
        private lateinit var mPackageManager: PackageManager
        private lateinit var mPackageManagerHidden: PackageManagerHidden
        private lateinit var mUserManager: UserManagerHidden

        fun onBind() {
            mSystemContext = ActivityThread.systemMain().systemContext
            mPackageManager = mSystemContext.packageManager
            mPackageManagerHidden = mPackageManager.castTo()
            mUserManager = UserManagerHidden.get(mSystemContext).castTo()
        }

        override fun testConnection() {}

        override fun getInstalledAppInfos(): ParcelFileDescriptor {
            return writeToParcel(context) { parcel ->
                val infos = mutableListOf<AppInfo>()
                val users = mUserManager.users
                users.forEach { user ->
                    infos.addAll(mPackageManagerHidden.getInstalledPackagesAsUser(0, user.id).map {
                        AppInfo(
                            packageName = it.packageName,
                            userId = user.id,
                            info = Info(
                                uid = it.applicationInfo?.uid ?: 0,
                                label = it.applicationInfo?.loadLabel(mPackageManager).toString(),
                                versionName = it.versionName ?: "",
                                versionCode = it.longVersionCode,
                                flags = it.applicationInfo?.flags ?: 0,
                                firstInstallTime = it.firstInstallTime,
                                lastUpdateTime = it.lastUpdateTime
                            )
                        )
                    })
                }
                parcel.writeTypedList(infos)
            }
        }

        override fun getInstalledAppStorages(): ParcelFileDescriptor {
            return writeToParcel(context) { parcel ->
                val packages = mutableListOf<Pair<Int, PackageInfo>>()
                val storages = mutableListOf<AppStorage>()
                val users = mUserManager.users
                users.forEach { user ->
                    packages.addAll(
                        mPackageManagerHidden.getInstalledPackagesAsUser(0, user.id)
                            .map { user.id to it })
                }
                storages.addAll(packages.map { (userId, item) ->
                    val apkBytes = runCatching {
                        item.applicationInfo?.sourceDir?.let { path -> File(path).parent }
                            ?.let { path -> NativeLib.calculateTreeSize(path) }
                    }.getOrNull() ?: 0
                    val userBytes = NativeLib.calculateTreeSize(
                        PathHelper.getAppUserDir(
                            userId,
                            item.packageName
                        )
                    )
                    val userDeBytes = NativeLib.calculateTreeSize(
                        PathHelper.getAppUserDeDir(
                            userId,
                            item.packageName
                        )
                    )
                    val dataBytes = NativeLib.calculateTreeSize(
                        PathHelper.getAppDataDir(
                            userId,
                            item.packageName
                        )
                    )
                    val obbBytes = NativeLib.calculateTreeSize(
                        PathHelper.getAppObbDir(
                            userId,
                            item.packageName
                        )
                    )
                    val mediaBytes = NativeLib.calculateTreeSize(
                        PathHelper.getAppMediaDir(
                            userId,
                            item.packageName
                        )
                    )
                    AppStorage(
                        packageName = item.packageName,
                        userId = userId,
                        storage = Storage(
                            apkBytes = apkBytes,
                            internalDataBytes = userBytes + userDeBytes,
                            externalDataBytes = dataBytes,
                            additionalDataBytes = obbBytes + mediaBytes,
                        )
                    )
                })
                parcel.writeTypedList(storages)
            }
        }

        override fun getUsers(): List<UserInfo> {
            return mUserManager.users
        }

        override fun getPrivilegedConfiguredNetworks(): List<BytesParcelable> {
            return emptyList()
        }

        override fun addNetworks(configs: List<BytesParcelable>): IntArray {
            return intArrayOf()
        }


        override fun getPackageSourceDir(packageName: String, userId: Int): List<String> {
            val sourceDirList = mutableListOf<String>()
            val packageInfo = mPackageManagerHidden.getPackageInfoAsUser(packageName, 0, userId)
            packageInfo.applicationInfo?.sourceDir?.also { sourceDirList.add(it) }
            val splitSourceDirs = packageInfo.applicationInfo?.splitSourceDirs
            if (!splitSourceDirs.isNullOrEmpty()) for (i in splitSourceDirs) sourceDirList.add(i)
            return sourceDirList
        }

        override fun getPackageInfo(packageName: String?, flags: Int, userId: Int): PackageInfo? {
            if (packageName == null) return null
            return try {
                mPackageManagerHidden.getPackageInfoAsUser(packageName, flags, userId)
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "getPackageInfo",
                    "Failed to get package info: $packageName",
                    e
                )
                null
            }
        }

        override fun getApplicationInfo(
            packageName: String?,
            flags: Int,
            userId: Int
        ): ApplicationInfo? {
            if (packageName == null) return null
            return try {
                mPackageManagerHidden.getApplicationInfoAsUser(packageName, flags, userId)
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "getApplicationInfo",
                    "Failed to get application info: $packageName",
                    e
                )
                null
            }
        }

        override fun loadLabel(packageName: String, userId: Int): String? {
            return try {
                val appInfo = mPackageManagerHidden.getApplicationInfoAsUser(packageName, 0, userId)
                appInfo?.loadLabel(mPackageManager)?.toString() ?: packageName
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "loadLabel",
                    "Failed to load label: $packageName",
                    e
                )
                packageName
            }
        }

        override fun loadIcon(packageName: String, userId: Int): Bitmap? {
            return try {
                val appInfo = mPackageManagerHidden.getApplicationInfoAsUser(packageName, 0, userId)
                Log.i("AppManageRootService", "loadIcon: $appInfo")
                val drawable = appInfo?.loadIcon(mPackageManager)
                drawableToBitmap(drawable)
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "loadIcon",
                    "Failed to load icon: $packageName",
                    e
                )
                null
            }
        }

        override fun getPermissions(packageName: String, userId: Int): List<AppPermission> {
            return try {
                val permissions = mutableListOf<AppPermission>()
                // 使用 PackageManagerHidden 获取权限信息
                val packageInfo = mPackageManagerHidden.getPackageInfoAsUser(
                    packageName,
                    PackageManager.GET_PERMISSIONS,
                    userId
                )

                packageInfo.requestedPermissions?.forEachIndexed { index, permissionName ->
                    val isGranted =
                        packageInfo.requestedPermissionsFlags?.getOrNull(index) ?: 0
                    val granted = (isGranted and PackageManager.PERMISSION_GRANTED) != 0
                    
                    // 获取权限的操作码
                    val op = runCatching {
                        val appOpsManagerClass = Class.forName("android.app.AppOpsManager")
                        val permissionToOpCodeMethod = appOpsManagerClass.getMethod("permissionToOpCode", String::class.java)
                        permissionToOpCodeMethod.invoke(null, permissionName) as Int
                    }.getOrElse { 
                        LogHelper.w(
                            "AppManageRootService",
                            "getPermissions",
                            "Failed to get op code for permission: $permissionName",
                            it as Exception
                        )
                        -1
                    }
                    
                    // 获取权限的mode值
                    val mode = if (op != -1) {
                        runCatching {
                            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE)
                            val appOpsManagerClass = appOpsManager?.javaClass
                            if (appOpsManagerClass != null) {
                                // 获取应用的UID
                                val appInfo = mPackageManagerHidden.getApplicationInfoAsUser(packageName, 0, userId)
                                val uid = appInfo?.uid ?: -1
                                
                                if (uid != -1) {
                                    // 反射调用 AppOpsManager.checkOpNoThrow(op, uid, packageName)
                                    val checkOpMethod = appOpsManagerClass.getMethod(
                                        "checkOpNoThrow", 
                                        Int::class.java, 
                                        Int::class.java, 
                                        String::class.java
                                    )
                                    checkOpMethod.invoke(appOpsManager, op, uid, packageName) as Int
                                } else {
                                    0 // 默认mode
                                }
                            } else {
                                0 // 默认mode
                            }
                        }.getOrElse { 
                            LogHelper.w(
                                "AppManageRootService",
                                "getPermissions",
                                "Failed to get mode for permission: $permissionName",
                                it as Exception
                            )
                            0 // 默认mode
                        }
                    } else {
                        0 // 默认mode
                    }
                    
                    permissions.add(
                        AppPermission(granted, mode, permissionName, op)
                    )
                }
                permissions
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "getPermissions",
                    "Failed to get permissions: $packageName",
                    e
                )
                emptyList()
            }
        }

        override fun setAppPermission(
            packageName: String?,
            userId: Int,
            permission: AppPermission?
        ) {
            if (packageName == null || permission == null) return
            try {
                val dpm =
                    context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager?

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
                    LogHelper.w(
                        "AppManageRootService",
                        "setAppPermission",
                        "Cannot set permissions without device admin rights. PackageName: $packageName, Permission: ${permission.name}"
                    )
                }
            } catch (e: SecurityException) {
                LogHelper.e(
                    "AppManageRootService",
                    "setAppPermission",
                    "Security exception: ${e.message}",
                    e
                )
            } catch (e: Exception) {
                LogHelper.e("AppManageRootService", "setAppPermission", "Error: ${e.message}", e)
            }
        }

        override fun setAppPermissions(
            packageName: String?,
            userId: Int,
            permissions: List<AppPermission>?
        ) {
            if (packageName == null || permissions == null) return
            // Apply each permission individually
            for (permission in permissions) {
                setAppPermission(packageName, userId, permission)
            }
        }

        override fun isInstalled(packageName: String?, userId: Int): Boolean {
            if (packageName == null) return false
            return try {
                mPackageManagerHidden.getPackageInfoAsUser(packageName, 0, userId)
                true
            } catch (e: Exception) {
                false
            }
        }

        override fun installApk(file: String?, userId: Int): Boolean {
            if (file == null) return false
                    
            try {
                // 使用 pm install 命令安装 APK
                // -r: 允许重新安装已存在的应用
                // -t: 允许安装测试应用
                val installCmd = "pm install -r -t --user $userId \"$file\""
                        
                LogHelper.d(
                    "AppManageRootService",
                    "installApk",
                    "Executing install command: $installCmd"
                )
                        
                // 获取 root shell 并执行安装命令
                val shell = com.topjohnwu.superuser.Shell.Builder.create()
                    .setFlags(com.topjohnwu.superuser.Shell.FLAG_MOUNT_MASTER)
                    .setCommands("su")
                    .setTimeout(120) // 设置较长的超时时间，因为安装可能需要较长时间
                    .build()
                        
                val result = shell.newJob().to(null, null).add(installCmd).exec()
                shell.close()
                        
                val output = result.out.joinToString("\n")
                val errOutput = result.err.joinToString("\n")
                        
                LogHelper.d(
                    "AppManageRootService",
                    "installApk",
                    "Install output: $output\nError output: $errOutput\nExit code: ${result.code}"
                )
                        
                // 检查安装结果
                return result.isSuccess && (
                    output.contains("Success") || 
                    output.contains("success") ||
                    output.contains("existing package") // 覆盖安装成功
                )
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "installApk",
                    "Failed to install APK: $file",
                    e
                )
                return false
            }
        }

        override fun uninstallApk(packageName: String?, userId: Int): Boolean {
            if (packageName == null) return false
            
            try {
                // 使用 pm uninstall 命令卸载应用
                val uninstallCmd = "pm uninstall --user $userId $packageName"
                
                LogHelper.d(
                    "AppManageRootService",
                    "uninstallApk",
                    "Executing uninstall command: $uninstallCmd"
                )
                
                // 获取 root shell 并执行卸载命令
                val shell = com.topjohnwu.superuser.Shell.Builder.create()
                    .setFlags(com.topjohnwu.superuser.Shell.FLAG_MOUNT_MASTER)
                    .setCommands("su")
                    .setTimeout(60)
                    .build()
                
                val result = shell.newJob().to(null, null).add(uninstallCmd).exec()
                shell.close()
                
                val output = result.out.joinToString("\n")
                val errOutput = result.err.joinToString("\n")
                
                LogHelper.d(
                    "AppManageRootService",
                    "uninstallApk",
                    "Uninstall output: $output\nError output: $errOutput\nExit code: ${result.code}"
                )
                
                // 检查卸载结果
                return result.isSuccess && (
                    output.contains("Success") || 
                    output.contains("success") ||
                    output.contains("not installed") // 如果应用未安装，也视为成功
                )
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "uninstallApk",
                    "Failed to uninstall APK: $packageName",
                    e
                )
                return false
            }
        }

        private fun isDeviceOwnerOrProfileOwner(dpm: DevicePolicyManager): Boolean {
            return try {
                dpm.isDeviceOwnerApp(context.packageName) || dpm.isProfileOwnerApp(context.packageName)
            } catch (e: Exception) {
                false
            }
        }

        private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable?): Bitmap? {
            if (drawable == null) return null

            if (drawable is BitmapDrawable) {
                if (drawable.bitmap != null) {
                    return drawable.bitmap
                }
            }

            val bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            } else {
                Bitmap.createBitmap(
                    drawable.intrinsicWidth,
                    drawable.intrinsicHeight,
                    Bitmap.Config.ARGB_8888
                )
            }

            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        }


        private fun writeToParcel(context: Context, block: (Parcel) -> Unit): ParcelFileDescriptor {
            val parcel = Parcel.obtain()
            parcel.setDataPosition(0)
            block(parcel)
            val tmpFile = File.createTempFile(
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


}
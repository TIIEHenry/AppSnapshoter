@file:Suppress("DEPRECATION")

package tiiehenry.android.snapshot.provider.appmanager.root

import android.app.ActivityManager
import android.app.ActivityManagerHidden
import android.app.ActivityThread
import android.app.AppOpsManager
import android.app.AppOpsManagerHidden
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManagerHidden
import android.content.pm.PermissionInfo
import android.content.pm.UserInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.RemoteException
import android.os.StatFs
import android.os.UserHandle
import android.os.UserHandleHidden
import android.os.UserManagerHidden
import android.system.Os
import android.util.Log
import androidx.core.content.pm.PermissionInfoCompat
import com.android.providers.settings.SettingsState
import com.android.providers.settings.SettingsStateApi26
import com.android.providers.settings.SettingsStateApi31
import com.github.luben.zstd.ZstdOutputStream
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import com.xayah.hiddenapi.castTo
import nota.android.io.NativeFileSystem
import nota.lang.reflect.ReflectionCache
import tiiehenry.android.compress.zstd.TarJNI
import tiiehenry.android.snapshot.app.AppPermission
import tiiehenry.android.snapshot.fs.IFileType
import tiiehenry.android.snapshot.provider.appmanager.model.AppInfo
import tiiehenry.android.snapshot.provider.appmanager.model.AppStorage
import tiiehenry.android.snapshot.provider.appmanager.model.Info
import tiiehenry.android.snapshot.provider.appmanager.model.Storage
import tiiehenry.android.snapshot.provider.appmanager.parcelables.BytesParcelable
import tiiehenry.android.snapshot.provider.appmanager.parcelables.FilePathParcelable
import tiiehenry.android.snapshot.provider.appmanager.parcelables.StatFsParcelable
import tiiehenry.android.snapshot.provider.appmanager.service.IBinaryCallback
import tiiehenry.android.snapshot.provider.appmanager.service.ISnapShotRootService
import tiiehenry.android.snapshot.provider.appmanager.util.CountingOutputStream
import tiiehenry.android.snapshot.provider.appmanager.util.LogHelper
import tiiehenry.android.snapshot.provider.appmanager.util.PathHelper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

class SnapshotRootService : RootService() {

    override fun onBind(intent: Intent): IBinder = Impl(applicationContext).apply { onBind() }

    private class Impl(private val context: Context) : ISnapShotRootService.Stub() {

        private lateinit var mSystemContext: Context
        private lateinit var mPackageManager: PackageManager
        private lateinit var mPackageManagerHidden: PackageManagerHidden
        private lateinit var mUserManager: UserManagerHidden
        private lateinit var mAppOpsManager: AppOpsManager
        private lateinit var mAppOpsManagerHidden: AppOpsManagerHidden
        private lateinit var mActivityManager: ActivityManager
        private lateinit var mActivityManagerHidden: ActivityManagerHidden

        fun onBind() {
            mSystemContext = ActivityThread.systemMain().systemContext
            mPackageManager = mSystemContext.packageManager
            mPackageManagerHidden = mPackageManager.castTo()
            mUserManager = UserManagerHidden.get(mSystemContext).castTo()
            mAppOpsManager = mSystemContext.getSystemService(Context.APP_OPS_SERVICE).castTo()
            mAppOpsManagerHidden = mAppOpsManager.castTo()
            mActivityManager = mSystemContext.getSystemService(Context.ACTIVITY_SERVICE).castTo()
            mActivityManagerHidden = mActivityManager.castTo()
        }

        // ==================== 通用工具方法 ====================

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

        // ==================== 连接测试 ====================

        override fun testConnection(): Boolean {
            return true
        }

        // ==================== 应用管理方法 ====================

        private val CORE_SYSTEM_PACKAGES = setOf(
            "android",
            "com.android.providers.settings",
            "com.android.providers.media",
            "com.android.providers.telephony",
            "com.android.providers.contacts",
            "com.android.providers.calendar",
            "com.android.shell",
        )

        override fun getInstalledAppInfos(): ParcelFileDescriptor {
            return writeToParcel(context) { parcel ->
                val infos = mutableListOf<AppInfo>()
                val users = mUserManager.users
                users.forEach { user ->
                    val installedPackagesAsUser =
                        mPackageManagerHidden.getInstalledPackagesAsUser(0, user.id)
                    installedPackagesAsUser.removeIf { it.packageName in CORE_SYSTEM_PACKAGES }
                    infos.addAll(installedPackagesAsUser.map {
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
                            ?.let { path -> NativeFileSystem.calculateTreeSize(path) }
                    }.getOrNull() ?: 0
                    val userBytes = NativeFileSystem.calculateTreeSize(
                        PathHelper.getAppUserDir(userId, item.packageName)
                    )
                    val userDeBytes = NativeFileSystem.calculateTreeSize(
                        PathHelper.getAppUserDeDir(userId, item.packageName)
                    )
                    val dataBytes = NativeFileSystem.calculateTreeSize(
                        PathHelper.getAppDataDir(userId, item.packageName)
                    )
                    val obbBytes = NativeFileSystem.calculateTreeSize(
                        PathHelper.getAppObbDir(userId, item.packageName)
                    )
                    val mediaBytes = NativeFileSystem.calculateTreeSize(
                        PathHelper.getAppMediaDir(userId, item.packageName)
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

        override fun getPackageInfo(packageName: String, flags: Int, userId: Int): PackageInfo? {
            return try {
                mPackageManagerHidden.getPackageInfoAsUser(packageName, flags, userId)
            } catch (e: Exception) {
                LogHelper.e("SnapshotRootService", "getPackageInfo", "Failed to get package info: $packageName: ${e.message}")
                null
            }
        }

        override fun getApplicationInfo(
            packageName: String,
            flags: Int,
            userId: Int
        ): ApplicationInfo? {
            return try {
                mPackageManagerHidden.getApplicationInfoAsUser(packageName, flags, userId)
            } catch (e: Exception) {
                LogHelper.e("SnapshotRootService", "getApplicationInfo", "Failed to get application info: $packageName: ${e.message}")
                null
            }
        }

        override fun loadLabel(packageName: String, userId: Int): String? {
            return try {
                val appInfo = mPackageManagerHidden.getApplicationInfoAsUser(packageName, 0, userId)
                appInfo?.loadLabel(mPackageManager)?.toString() ?: packageName
            } catch (e: Exception) {
                LogHelper.e("SnapshotRootService", "loadLabel", "Failed to load label: $packageName: ${e.message}")
                null
            }
        }

        override fun loadIcon(packageName: String, userId: Int): Bitmap? {
            return try {
                val appInfo = mPackageManagerHidden.getApplicationInfoAsUser(packageName, 0, userId)
                val drawable = appInfo?.loadIcon(mPackageManager)
                drawableToBitmap(drawable)
            } catch (e: Exception) {
                throw RemoteException("Failed to load icon: $packageName: ${e.message}")
            }
        }

        override fun getPermissions(packageName: String, userId: Int): List<AppPermission> {
            return try {
                val permissions = mutableListOf<AppPermission>()
                val packageInfo = mPackageManagerHidden.getPackageInfoAsUser(
                    packageName,
                    PackageManager.GET_PERMISSIONS,
                    userId
                ) ?: throw RemoteException("Failed to get package info: $packageName")
                val uid = packageInfo.applicationInfo?.uid
                    ?: throw RemoteException("Failed to get uid: $packageName")
                val requestedPermissions = packageInfo.requestedPermissions?.toList() ?: listOf()
                val requestedPermissionsFlags =
                    packageInfo.requestedPermissionsFlags?.toList() ?: listOf()
                val ops: Map<Int, Int>? = try {
                    val reflection = ReflectionCache.build()
                    val getOpsForPackageMethod = reflection.getMethod(
                        mAppOpsManager.javaClass,
                        "getOpsForPackage",
                        Int::class.java,
                        String::class.java,
                        Array<String>::class.java
                    )
                    val opsResult =
                        getOpsForPackageMethod.invoke(mAppOpsManager, uid, packageName, null)
                    val opsList = opsResult as? List<*>
                    val resultMap = mutableMapOf<Int, Int>()
                    opsList?.firstOrNull()?.let { pkgOps ->
                        val opList = reflection.getMethod(pkgOps.javaClass, "getOps")
                            .invoke(pkgOps) as? List<*>
                        opList?.forEach { opEntry ->
                            val getOpMethod = reflection.getMethod(opEntry!!.javaClass, "getOp")
                            val getModeMethod = reflection.getMethod(opEntry.javaClass, "getMode")
                            val opValue = getOpMethod.invoke(opEntry) as? Int
                            val modeValue = getModeMethod.invoke(opEntry) as? Int
                            if (opValue != null && modeValue != null) {
                                resultMap[opValue] = modeValue
                            }
                        }
                    }
                    resultMap
                } catch (e: Exception) {
                    throw RemoteException("Failed to get ops: ${e.message}")
                }
                requestedPermissions.forEachIndexed { i, name ->
                    runCatching {
                        val permissionInfo = mPackageManager.getPermissionInfo(name, 0)
                        val protection = PermissionInfoCompat.getProtection(permissionInfo)
                        val protectionFlags =
                            PermissionInfoCompat.getProtectionFlags(permissionInfo)
                        val isGranted =
                            (requestedPermissionsFlags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                        val op = AppOpsManagerHidden.permissionToOpCode(name)
                        val mode = ops?.get(op)
                        if ((op != AppOpsManagerHidden.OP_NONE)
                            || (protection == PermissionInfo.PROTECTION_DANGEROUS || (protectionFlags and PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0)
                        ) {
                            permissions.add(AppPermission(isGranted, mode ?: 0, name, op))
                        }
                    }
                }
                permissions
            } catch (e: Exception) {
                throw RemoteException("Failed to get permissions: $packageName: ${e.message}")
            }
        }

        override fun setAppPermission(packageName: String, userId: Int, permission: AppPermission): Boolean {
            val userHandle = getUserHandle(userId) ?: run {
                LogHelper.e("SnapshotRootService", "setAppPermission", "Failed to get user handle for userId: $userId")
                return false
            }
            return try {
                if (permission.isGranted) {
                    grantRuntimePermission(packageName, permission.name, userHandle)
                } else {
                    revokeRuntimePermission(packageName, permission.name, userHandle)
                }
                if (permission.op != AppOpsManagerHidden.OP_NONE) {
                    val uid = getPackageUid(packageName, userId)
                    setOpsMode(permission.op, uid, packageName, permission.mode)
                }
                LogHelper.i("SnapshotRootService", "setAppPermission", "Successfully set permission for $packageName")
                true
            } catch (e: SecurityException) {
                LogHelper.e("SnapshotRootService", "setAppPermission", "Security exception: ${e.message}")
                false
            } catch (e: Exception) {
                LogHelper.e("SnapshotRootService", "setAppPermission", "Error: ${e.message}")
                false
            }
        }

        override fun setAppPermissions(
            packageName: String,
            userId: Int,
            permissions: List<AppPermission>
        ): Boolean {
            return try {
                for (permission in permissions) {
                    if (!setAppPermission(packageName, userId, permission)) {
                        LogHelper.e("SnapshotRootService", "setAppPermissions", "Failed to set permission ${permission.name} for $packageName")
                        return false
                    }
                }
                LogHelper.i("SnapshotRootService", "setAppPermissions", "Successfully set all permissions for $packageName")
                true
            } catch (e: Exception) {
                LogHelper.e("SnapshotRootService", "setAppPermissions", "Error: ${e.message}")
                false
            }
        }

        override fun isInstalled(packageName: String, userId: Int): Boolean {
            return try {
                mPackageManagerHidden.getPackageInfoAsUser(packageName, 0, userId)
                true
            } catch (e: Exception) {
                false
            }
        }

        override fun installApk(file: String, userId: Int): Boolean {
            return try {
                val installCmd = "pm install -r -t --user $userId \"$file\""
                val shell = Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(120)
                    .build()
                val result = shell.newJob().to(null, null).add(installCmd).exec()
                shell.close()
                if (result.isSuccess) {
                    LogHelper.i("SnapshotRootService", "installApk", "Successfully installed APK: $file")
                    true
                } else {
                    LogHelper.e("SnapshotRootService", "installApk", "Failed to install APK: $file")
                    false
                }
            } catch (e: Exception) {
                LogHelper.e("SnapshotRootService", "installApk", "Error: ${e.message}")
                false
            }
        }

        override fun installApks(files: List<String>, userId: Int): Boolean {
            if (files.isEmpty()) {
                LogHelper.e("SnapshotRootService", "installApks", "Files list is empty")
                return false
            }
            return try {
                val shell = Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(300)
                    .build()
                val createCmd = "pm install-create --user $userId"
                var createResult = shell.newJob().to(null, null).add(createCmd).exec()
                var output = createResult.out.joinToString("\n")
                val sessionMatch = Regex("\\[(\\d+)\\]").find(output)
                if (sessionMatch == null) {
                    shell.close()
                    LogHelper.e("SnapshotRootService", "installApks", "Failed to get session ID from output: $output")
                    return false
                }
                val sessionId = sessionMatch.groupValues[1]
                for ((index, filePath) in files.withIndex()) {
                    val file = File(filePath)
                    val fileSize = file.length()
                    val splitName = if (index == 0) "base" else "split_$index"
                    val writeCmd =
                        "pm install-write -S $fileSize - $sessionId $splitName \"$filePath\""
                    createResult = shell.newJob().to(null, null).add(writeCmd).exec()
                    output = createResult.out.joinToString("\n")
                    if (!createResult.isSuccess && !output.contains("Success")) {
                        shell.newJob().to(null, null).add("pm install-abandon $sessionId").exec()
                        shell.close()
                        LogHelper.e("SnapshotRootService", "installApks", "Failed to write APK: $filePath")
                        return false
                    }
                }
                val commitCmd = "pm install-commit $sessionId"
                createResult = shell.newJob().to(null, null).add(commitCmd).exec()
                shell.close()
                if (createResult.isSuccess) {
                    LogHelper.i("SnapshotRootService", "installApks", "Successfully installed APKs: ${files.joinToString(", ")}")
                    true
                } else {
                    LogHelper.e("SnapshotRootService", "installApks", "Failed to install APKs: ${files.joinToString(", ")}")
                    false
                }
            } catch (e: Exception) {
                LogHelper.e("SnapshotRootService", "installApks", "Error: ${e.message}")
                false
            }
        }

        override fun uninstallApk(packageName: String, userId: Int): Boolean {
            return try {
                val uninstallCmd = "pm uninstall --user $userId $packageName"
                val shell = Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(60)
                    .build()
                val result = shell.newJob().to(null, null).add(uninstallCmd).exec()
                shell.close()
                val output = result.out.joinToString("\n")
                val success = result.isSuccess && (
                        output.contains("Success") ||
                                output.contains("success") ||
                                output.contains("not installed")
                        )
                if (success) {
                    LogHelper.i("SnapshotRootService", "uninstallApk", "Successfully uninstalled APK: $packageName")
                    true
                } else {
                    LogHelper.e("SnapshotRootService", "uninstallApk", "Failed to uninstall APK: $packageName")
                    false
                }
            } catch (e: Exception) {
                LogHelper.e("SnapshotRootService", "uninstallApk", "Error: ${e.message}")
                false
            }
        }

        override fun forceStopPackage(packageName: String, userId: Int): Boolean {
            return try {
                val cmd = "am force-stop --user $userId $packageName"
                val shell = Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(30)
                    .build()
                val result = shell.newJob().to(null, null).add(cmd).exec()
                shell.close()
                if (result.isSuccess) {
                    LogHelper.i("SnapshotRootService", "forceStopPackage", "Successfully force-stopped package: $packageName")
                    true
                } else {
                    LogHelper.e("SnapshotRootService", "forceStopPackage", "Failed to force-stop package: $packageName")
                    false
                }
            } catch (e: Exception) {
                LogHelper.e("SnapshotRootService", "forceStopPackage", "Error: ${e.message}")
                false
            }
        }

        override fun clearAppData(packageName: String, userId: Int): Boolean {
            return try {
                val cmd = "pm clear --user $userId $packageName"
                val shell = Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(60)
                    .build()
                val result = shell.newJob().to(null, null).add(cmd).exec()
                shell.close()
                if (result.isSuccess) {
                    LogHelper.i("SnapshotRootService", "clearAppData", "Successfully cleared app data: $packageName")
                    true
                } else {
                    LogHelper.e("SnapshotRootService", "clearAppData", "Failed to clear app data: $packageName")
                    false
                }
            } catch (e: Exception) {
                LogHelper.e("SnapshotRootService", "clearAppData", "Error: ${e.message}")
                false
            }
        }

        override fun suspendPackage(packageName: String, userId: Int): Boolean {
            return try {
                val cmd = "pm suspend $packageName"
                val shell = Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(30)
                    .build()
                val result = shell.newJob().to(null, null).add(cmd).exec()
                shell.close()
                if (result.isSuccess) {
                    LogHelper.i("SnapshotRootService", "suspendPackage", "Successfully suspended package: $packageName")
                    true
                } else {
                    LogHelper.e("SnapshotRootService", "suspendPackage", "Failed to suspend package: $packageName")
                    false
                }
            } catch (e: Exception) {
                LogHelper.e("SnapshotRootService", "suspendPackage", "Error: ${e.message}")
                false
            }
        }

        override fun unsuspendPackage(packageName: String, userId: Int): Boolean {
            return try {
                val cmd = "pm unsuspend $packageName"
                val shell = Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(30)
                    .build()
                val result = shell.newJob().to(null, null).add(cmd).exec()
                shell.close()
                if (result.isSuccess) {
                    LogHelper.i("SnapshotRootService", "unsuspendPackage", "Successfully unsuspended package: $packageName")
                    true
                } else {
                    LogHelper.e("SnapshotRootService", "unsuspendPackage", "Failed to unsuspend package: $packageName")
                    false
                }
            } catch (e: Exception) {
                LogHelper.e("SnapshotRootService", "unsuspendPackage", "Error: ${e.message}")
                false
            }
        }

        override fun grantRuntimePermission(
            packageName: String,
            permName: String,
            user: UserHandle
        ): Int {
            return try {
                mPackageManagerHidden.grantRuntimePermission(packageName, permName, user)
                LogHelper.i("SnapshotRootService", "grantRuntimePermission", "Successfully granted permission: $permName for $packageName")
                1 // 成功
            } catch (e: SecurityException) {
                if (e.message?.contains("is not a changeable permission type") == true) {
                    LogHelper.w("SnapshotRootService", "grantRuntimePermission", "not a changeable permission type: $permName for $packageName")
                    -1 // fixed permission type
                } else {
                    LogHelper.e("SnapshotRootService", "grantRuntimePermission", "Failed to grant permission: $permName for $packageName: ${e.message}")
                    0 // 失败
                }
            } catch (e: Exception) {
                LogHelper.e("SnapshotRootService", "grantRuntimePermission", "Failed to grant permission: $permName for $packageName: ${e.message}")
                0 // 失败
            }
        }

        override fun revokeRuntimePermission(
            packageName: String,
            permName: String,
            user: UserHandle?
        ): Int {
            if (user == null) {
                LogHelper.e("SnapshotRootService", "revokeRuntimePermission", "User handle is null")
                return 0 // 失败
            }
            return try {
                mPackageManagerHidden.revokeRuntimePermission(packageName, permName, user)
                LogHelper.i("SnapshotRootService", "revokeRuntimePermission", "Successfully revoked permission: $permName for $packageName")
                1 // 成功
            } catch (e: SecurityException) {
                if (e.message?.contains("is not a changeable permission type") == true) {
                    LogHelper.w("SnapshotRootService", "revokeRuntimePermission", "not a changeable permission type: $permName for $packageName")
                    -1 // fixed permission type
                } else {
                    LogHelper.e("SnapshotRootService", "revokeRuntimePermission", "Failed to revoke permission: $permName for $packageName: ${e.message}")
                    0 // 失败
                }
            } catch (e: Exception) {
                LogHelper.e("SnapshotRootService", "revokeRuntimePermission", "Failed to revoke permission: $permName for $packageName: ${e.message}")
                0 // 失败
            }
        }

        override fun getPermissionFlags(
            packageName: String,
            permName: String,
            user: UserHandle
        ): Int {
            return try {
                mPackageManagerHidden.getPermissionFlags(permName, packageName, user)
            } catch (e: Exception) {
                throw RemoteException("Failed to get permission flags: $permName for $packageName: ${e.message}")
            }
        }

        override fun updatePermissionFlags(
            packageName: String,
            permName: String,
            user: UserHandle,
            flagMask: Int,
            flagValues: Int
        ): Boolean {
            return try {
                mPackageManagerHidden.updatePermissionFlags(
                    permName,
                    packageName,
                    flagMask,
                    flagValues,
                    user
                )
                LogHelper.i("SnapshotRootService", "updatePermissionFlags", "Successfully updated permission flags for $permName in $packageName")
                true
            } catch (e: Exception) {
                LogHelper.e("SnapshotRootService", "updatePermissionFlags", "Failed to update permission flags: $permName for $packageName: ${e.message}")
                false
            }
        }

        override fun getPackageUid(packageName: String, userId: Int): Int {
            return try {
                mPackageManagerHidden.getPackageInfoAsUser(
                    packageName,
                    0,
                    userId
                ).applicationInfo?.uid ?: -1
            } catch (e: Exception) {
                throw RemoteException("Failed to get uid for: $packageName: ${e.message}")
            }
        }

        override fun getUserHandle(userId: Int): UserHandle? {
            return try {
                UserHandleHidden.of(userId)
            } catch (e: Exception) {
                throw RemoteException("Failed to get user handle for: $userId: ${e.message}")
            }
        }

        override fun setOpsMode(code: Int, uid: Int, packageName: String, mode: Int): Boolean {
            return try {
                val appOpsManagerHidden = mAppOpsManager.castTo<AppOpsManagerHidden>()
                appOpsManagerHidden.setMode(code, uid, packageName, mode)
                LogHelper.i("SnapshotRootService", "setOpsMode", "Successfully set ops mode for $packageName")
                true
            } catch (e: Exception) {
                LogHelper.e("SnapshotRootService", "setOpsMode", "Failed to set ops mode for $packageName: ${e.message}")
                false
            }
        }

        override fun resetAppOps(userId: Int, packageName: String?): Boolean {
            if (packageName == null) {
                LogHelper.e("SnapshotRootService", "resetAppOps", "Package name is null")
                return false
            }
            return try {
                com.topjohnwu.superuser.ShellUtils.fastCmd("appops reset --user $userId $packageName")
                LogHelper.i("SnapshotRootService", "resetAppOps", "Successfully reset appops for $packageName")
                true
            } catch (e: Exception) {
                LogHelper.e("SnapshotRootService", "resetAppOps", "Failed to reset appops for $packageName: ${e.message}")
                false
            }
        }

        override fun getPackageSsaidAsUser(packageName: String, uid: Int, userId: Int): String? {
            return try {
                val settingsState = getSettingsState(userId)
                settingsState.getSettingLocked(getSsaidName(packageName, uid))?.value
            } catch (e: Exception) {
                throw RemoteException("Failed to get ssaid for: $packageName: ${e.message}")
            }
        }

        override fun setPackageSsaidAsUser(
            packageName: String,
            uid: Int,
            userId: Int,
            ssaid: String
        ): Boolean {
            return try {
                val settingsState = getSettingsState(userId)
                settingsState.insertSettingLocked(
                    getSsaidName(packageName, uid),
                    ssaid,
                    null,
                    true,
                    packageName
                )
                LogHelper.i("SnapshotRootService", "setPackageSsaidAsUser", "Successfully set ssaid for $packageName")
                true
            } catch (e: Exception) {
                LogHelper.e("SnapshotRootService", "setPackageSsaidAsUser", "Failed to set ssaid for $packageName: ${e.message}")
                false
            }
        }

        override fun isPackageRunning(packageName: String, userId: Int): Boolean {
            return try {
                val runningProcesses = mActivityManagerHidden.getRunningAppProcesses()
                runningProcesses?.any { process ->
                    process.processName == packageName ||
                            process.processName.startsWith("$packageName:")
                } ?: false
            } catch (e: Exception) {
                throw RemoteException("Failed to check if package is running: $packageName: ${e.message}")
            }
        }

        override fun launchApp(packageName: String, userId: Int): Boolean {
            return try {
                val shell = Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(30)
                    .build()
        
                // 优先检测应用是否已在后台运行，若是则通过 am moveTaskToFront 将其前台化
                val isRunning = isPackageRunning(packageName, userId)
                if (isRunning) {
                    // 从 dumpsys activity tasks 中查找该包名且属于指定 userId 的 taskId
                    //
                    // 真实输出格式（Android 10+）：
                    //   * Task{6e2c6b8 #42 visible=false mode=fullscreen translucent=false}
                    //       userId=0 effectiveUid=u0a123 mCallingUid=u0a123 ...
                    //       intent={...cmp=com.example.app/.MainActivity}
                    //       realActivity=com.example.app/.MainActivity
                    //
                    // taskId 在 Task{} 中用 #<id> 表示，userId 在紧随其后的行
                    val dumpsysResult = shell.newJob().to(null, null)
                        .add("dumpsys activity tasks")
                        .exec()
                    val lines = dumpsysResult.out
                    var taskId: Int? = null
                    var pendingTaskId: Int? = null   // 当前 Task 块解析到的 taskId
                    var pendingUserId: Int? = null   // 当前 Task 块解析到的 userId
                    for (line in lines) {
                        // 匹配 Task 块头部："* Task{... #42 ...}" 或 "taskId=42"
                        // 两种格式都尝试提取
                        val taskHashMatch = Regex("\\* Task\\{[^}]* #(\\d+)").find(line)
                        val taskIdFieldMatch = Regex("\\btaskId=(\\d+)").find(line)
                        val newTaskId = (taskHashMatch?.groupValues?.get(1)
                            ?: taskIdFieldMatch?.groupValues?.get(1))?.toIntOrNull()
                        if (newTaskId != null) {
                            pendingTaskId = newTaskId
                            pendingUserId = null // 重置，等待后续行提供 userId
                            // 有些版本 userId 和 taskId 在同一行，一并提取
                            Regex("\\buserId=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()
                                ?.let {
                                    pendingUserId = it
                                }
                        }
                        // 匹配独立的 userId 行，格式："  userId=0 ..."
                        if (pendingUserId == null) {
                            Regex("\\buserId=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()
                                ?.let {
                                    pendingUserId = it
                                }
                        }
                        // 匹配含目标包名的 activity 行
                        if (line.contains(packageName) &&
                            (line.contains("baseActivity=") || line.contains("realActivity=") ||
                                    line.contains("origActivity=") || line.contains("cmp=$packageName"))
                        ) {
                            if (pendingTaskId != null && pendingUserId == userId) {
                                taskId = pendingTaskId
                                break
                            }
                        }
                    }
                    if (taskId != null) {
                        val moveResult = shell.newJob().to(null, null)
                            .add("am moveTaskToFront $taskId")
                            .exec()
                        shell.close()
                        if (moveResult.isSuccess) return true
                        // moveTaskToFront 失败则继续降级用 am start
                    }
                }
        
                // 应用未在运行，或 moveTaskToFront 降级：通过 am start 命令行以 root 权限启动，支持多用户
                val cmd =
                    "am start --user $userId -a android.intent.action.MAIN -c android.intent.category.LAUNCHER $packageName"
                val result = shell.newJob().to(null, null).add(cmd).exec()
                shell.close()
                result.isSuccess
            } catch (e: Exception) {
                throw RemoteException("Failed to launch app: $packageName: ${e.message}")
            }
        }

        // ==================== 文件系统方法 ====================

        override fun readStatFs(path: String): StatFsParcelable {
            val statFs = StatFs(path)
            return StatFsParcelable(statFs.availableBytes, statFs.totalBytes)
        }

        override fun listFilePaths(
            path: String,
            listFiles: Boolean,
            listDirs: Boolean
        ): List<FilePathParcelable> {
            return File(path).listFiles()?.filter {
                (it.isFile && listFiles) || (it.isDirectory && listDirs)
            }?.map {
                FilePathParcelable(it.path, if (it.isFile) 0 else if (it.isDirectory) 1 else -1)
            } ?: listOf()
        }

        override fun readText(path: String): ParcelFileDescriptor {
            return writeToParcel(context) { parcel ->
                parcel.writeString(runCatching { File(path).readText() }.getOrNull() ?: "")
            }
        }

        override fun writeText(path: String, pfd: ParcelFileDescriptor): Boolean {
            return try {
                var text = ""
                readFromParcel(pfd) { parcel -> parcel.readString()?.also { text = it } }
                val textFile = File(path)
                if (textFile.isDirectory || textFile.exists()) {
                    textFile.deleteRecursively()
                }
                textFile.createNewFile()
                textFile.writeText(text)
                LogHelper.i("SnapshotRootService", "writeText", "Successfully wrote text to $path")
                true
            } catch (e: Exception) {
                LogHelper.e("SnapshotRootService", "writeText", "Failed to write text to $path: ${e.message}")
                false
            }
        }

        override fun calculateTreeSize(path: String): Long {
            return NativeFileSystem.calculateTreeSize(path)
        }

        override fun callTarCli(stdOut: String, stdErr: String, argv: Array<String>): Int {
            return TarJNI.callCli(stdOut, stdErr, argv)
        }

        override fun compress(
            level: Int,
            inputPath: String,
            outputPath: String,
            callback: IBinaryCallback?
        ): String? {
            runCatching {
                FileInputStream(inputPath).use { fileInputStream ->
                    FileOutputStream(outputPath).use { fileOutputStream ->
                        CountingOutputStream(
                            source = fileOutputStream,
                            onProgress = if (callback != null) { bytesWritten, speed ->
                                callback.onProgress(bytesWritten, speed)
                            } else null
                        ).use { countingOutputStream ->
                            ZstdOutputStream(countingOutputStream, level).use { zstdOutputStream ->
                                zstdOutputStream.setWorkers(
                                    Runtime.getRuntime().availableProcessors()
                                )
                                fileInputStream.copyTo(zstdOutputStream)
                            }
                        }
                    }
                }
            }.onFailure { return it.message }
            return null
        }

        override fun mkdirs(path: String): Boolean {
            return runCatching {
                val file = File(path)
                if (file.exists().not()) file.mkdirs() else true
            }.getOrNull() ?: false
        }

        override fun exists(path: String): Boolean {
            return runCatching { File(path).exists() }.getOrNull() ?: false
        }

        override fun fileType(path: String): Int {
            val file = File(path)
            return when {
                !file.exists() -> IFileType.TYPE_NONE
                file.isDirectory -> IFileType.TYPE_DIR
                file.isFile -> IFileType.TYPE_FILE
                else -> IFileType.TYPE_OTHER
            }
        }

        override fun deleteRecursively(path: String): Boolean {
            return runCatching { File(path).deleteRecursively() }.getOrNull() ?: false
        }

        override fun copyRecursively(source: String, target: String, overwrite: Boolean): Boolean {
            return runCatching { File(source).copyRecursively(File(target), overwrite) }.getOrNull()
                ?: false
        }

        override fun getLastModifiedTime(path: String): Long {
            return runCatching { File(path).lastModified() }.getOrNull() ?: 0L
        }

        override fun setLastModifiedTime(path: String, time: Long): Boolean {
            return runCatching { File(path).setLastModified(time) }.getOrNull() ?: false
        }

        override fun getUid(path: String): Int {
            return runCatching {
                val stat = Os.stat(path)
                stat.st_uid
            }.getOrNull() ?: -1
        }

        override fun setUid(path: String, uid: Int): Boolean {
            return runCatching {
                Os.chown(path, uid, -1)
                true
            }.getOrNull() ?: false
        }

        override fun getGid(path: String): Int {
            return runCatching {
                val stat = Os.stat(path)
                stat.st_gid
            }.getOrNull() ?: -1
        }

        override fun setGid(path: String, gid: Int): Boolean {
            return runCatching {
                Os.chown(path, -1, gid)
                true
            }.getOrNull() ?: false
        }

        override fun openFile(path: String, mode: Int): ParcelFileDescriptor? {
            return runCatching {
                val file = File(path)
                if (mode and ParcelFileDescriptor.MODE_CREATE != 0 && !file.exists()) {
                    file.parentFile?.mkdirs()
                    file.createNewFile()
                }
                ParcelFileDescriptor.open(file, mode)
            }.getOrNull()
        }

        override fun md5(file: String): String? {
            return runCatching {
                val digest = MessageDigest.getInstance("MD5")
                FileInputStream(file).use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        digest.update(buffer, 0, bytesRead)
                    }
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }.getOrNull()
        }

        override fun extractTar(tarFifo: String, targetDir: String): Boolean {
            return runCatching {
                File(targetDir).mkdirs()
                val stdOut =
                    File.createTempFile("tar-stdout-", ".log", context.cacheDir).absolutePath
                val stdErr =
                    File.createTempFile("tar-stderr-", ".log", context.cacheDir).absolutePath
                try {
                    val argv = arrayOf("tar", "-xpf", tarFifo, "-C", targetDir)
                    val exitCode = callTarCli(stdOut, stdErr, argv)
                    if (exitCode == 0) {
                        LogHelper.i("SnapshotRootService", "extractTar", "Successfully extracted tar to $targetDir")
                        true
                    } else {
                        LogHelper.e("SnapshotRootService", "extractTar", "Failed to extract tar, exit code: $exitCode")
                        false
                    }
                } finally {
                    runCatching {
                        File(stdOut).delete()
                        File(stdErr).delete()
                    }
                }
            }.onFailure { exception ->
                LogHelper.e("SnapshotRootService", "extractTar", "Failed to extract tar: ${exception.message}")
            }.getOrNull() ?: false
        }

        // ==================== 私有辅助方法 ====================

        private fun getSettingsState(userId: Int): SettingsState {
            val lock = Object()
            val thread = HandlerThread("ssaid_handler", Process.THREAD_PRIORITY_BACKGROUND)
            thread.start()
            val file = File("/data/system/users/$userId/settings_ssaid.xml")
            val key = SettingsState.makeKey(SettingsState.SETTINGS_TYPE_SSAID, userId)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingsStateApi31(
                    lock,
                    file,
                    key,
                    SettingsState.MAX_BYTES_PER_APP_PACKAGE_UNLIMITED,
                    thread.looper
                )
            } else {
                SettingsStateApi26(
                    lock,
                    file,
                    key,
                    SettingsState.MAX_BYTES_PER_APP_PACKAGE_UNLIMITED,
                    thread.looper
                )
            }
        }

        private fun getSsaidName(packageName: String, uid: Int): String {
            return if (packageName == SettingsState.SYSTEM_PACKAGE_NAME) "userkey" else uid.toString()
        }

        private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
            if (drawable == null) return null
            if (drawable is BitmapDrawable) {
                if (drawable.bitmap != null) return drawable.bitmap
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
    }
}

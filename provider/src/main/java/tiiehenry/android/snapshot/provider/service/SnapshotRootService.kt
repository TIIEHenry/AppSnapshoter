@file:Suppress("DEPRECATION")

package tiiehenry.android.snapshot.provider.service

import android.app.ActivityManagerHidden
import android.app.AppOpsManager
import android.app.AppOpsManagerHidden
import android.app.ActivityThread
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManagerHidden
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.UserHandle
import android.os.UserManagerHidden
import com.topjohnwu.superuser.ipc.RootService
import nota.android.hiddenapi.castTo
import tiiehenry.android.snapshot.app.AppInfo
import tiiehenry.android.snapshot.app.AppPermission
import tiiehenry.android.snapshot.app.AppStorage
import tiiehenry.android.snapshot.provider.service.bean.StatFsResult
import tiiehenry.android.snapshot.provider.service.handler.AppManagementHandler
import tiiehenry.android.snapshot.provider.service.handler.FileSystemHandler
import tiiehenry.android.snapshot.provider.service.handler.PermissionManagementHandler
import tiiehenry.android.snapshot.provider.service.handler.SsaidManagementHandler
import android.content.pm.PackageInfo
import android.content.pm.UserInfo
import android.graphics.Bitmap
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo

class SnapshotRootService : RootService() {

    override fun onBind(intent: Intent): IBinder = Impl(applicationContext).apply { onBind() }

    private class Impl(private val context: Context) : ISnapShotRootService.Stub() {

        private lateinit var appManager: AppManagementHandler
        private lateinit var permissionManager: PermissionManagementHandler
        private lateinit var fileSystem: FileSystemHandler
        private val ssaidManager = SsaidManagementHandler()

        fun onBind() {
            val systemContext: Context = ActivityThread.systemMain().systemContext
            val packageManager = systemContext.getPackageManager()
            val packageManagerHidden: PackageManagerHidden = packageManager.castTo()
            val userManager: UserManagerHidden = UserManagerHidden.get(systemContext).castTo()
            val appOpsManager: AppOpsManager = systemContext.getSystemService(APP_OPS_SERVICE).castTo()
            val appOpsManagerHidden: AppOpsManagerHidden = appOpsManager.castTo()
            val activityManagerHidden: ActivityManagerHidden =
                systemContext.getSystemService(ACTIVITY_SERVICE).castTo()

            appManager = AppManagementHandler(
                context, packageManager, packageManagerHidden, userManager, activityManagerHidden
            )
            permissionManager = PermissionManagementHandler(
                context, packageManager, packageManagerHidden, appOpsManager, appOpsManagerHidden
            )
            fileSystem = FileSystemHandler(context)
        }

        // ==================== 连接测试 ====================

        override fun testConnection(): Boolean = true

        // ==================== 应用管理 ====================

        override fun getInstalledAppInfos(): List<AppInfo> = appManager.getInstalledAppInfos()

        override fun getInstalledAppStorages(): List<AppStorage> = appManager.getInstalledAppStorages()

        override fun getUsers(): List<UserInfo> = appManager.getUsers()

        override fun getPackageSourceDir(packageName: String, userId: Int): List<String> =
            appManager.getPackageSourceDir(packageName, userId)

        override fun getPackageInfo(packageName: String, flags: Int, userId: Int): PackageInfo? =
            appManager.getPackageInfo(packageName, flags, userId)

        override fun getApplicationInfo(packageName: String, flags: Int, userId: Int): ApplicationInfo? =
            appManager.getApplicationInfo(packageName, flags, userId)

        override fun loadLabel(packageName: String, userId: Int): String? =
            appManager.loadLabel(packageName, userId)

        override fun loadIcon(packageName: String, userId: Int): Bitmap? =
            appManager.loadIcon(packageName, userId)

        override fun isInstalled(packageName: String, userId: Int): Boolean =
            appManager.isInstalled(packageName, userId)

        override fun installApk(file: String, userId: Int): Boolean =
            appManager.installApk(file, userId)

        override fun installApks(files: List<String>, userId: Int): Boolean =
            appManager.installApks(files, userId)

        override fun uninstallApk(packageName: String, userId: Int): Boolean =
            appManager.uninstallApk(packageName, userId)

        override fun forceStopPackage(packageName: String, userId: Int): Boolean =
            appManager.forceStopPackage(packageName, userId)

        override fun clearAppData(packageName: String, userId: Int): Boolean =
            appManager.clearAppData(packageName, userId)

        override fun suspendPackage(packageName: String, userId: Int): Boolean =
            appManager.suspendPackage(packageName, userId)

        override fun unsuspendPackage(packageName: String, userId: Int): Boolean =
            appManager.unsuspendPackage(packageName, userId)

        override fun isPackageRunning(packageName: String, userId: Int): Boolean =
            appManager.isPackageRunning(packageName, userId)

        override fun launchApp(packageName: String, userId: Int): Boolean =
            appManager.launchApp(packageName, userId)

        // ==================== 权限管理 ====================

        override fun getPermissions(packageName: String, userId: Int): List<AppPermission> =
            permissionManager.getPermissions(packageName, userId)

        override fun setAppPermission(
            packageName: String, userId: Int, permission: AppPermission
        ): Boolean = permissionManager.setAppPermission(
            packageName, userId, permission,
            getUserHandle = { permissionManager.getUserHandle(it) },
            grantRuntimePermission = { pkg, perm, user -> permissionManager.grantRuntimePermission(pkg, perm, user) },
            revokeRuntimePermission = { pkg, perm, user -> permissionManager.revokeRuntimePermission(pkg, perm, user) },
            getPackageUid = { pkg, uid -> permissionManager.getPackageUid(pkg, uid) },
            setOpsMode = { code, uid, pkg, mode -> permissionManager.setOpsMode(code, uid, pkg, mode) }
        )

        override fun setAppPermissions(
            packageName: String, userId: Int, permissions: List<AppPermission>
        ): Boolean = permissionManager.setAppPermissions(
            packageName, userId, permissions,
            setAppPermission = { pkg, uid, perm -> setAppPermission(pkg, uid, perm) }
        )

        override fun grantRuntimePermission(
            packageName: String, permName: String, user: UserHandle
        ): Int = permissionManager.grantRuntimePermission(packageName, permName, user)

        override fun revokeRuntimePermission(
            packageName: String, permName: String, user: UserHandle?
        ): Int = permissionManager.revokeRuntimePermission(packageName, permName, user)

        override fun getPermissionFlags(
            packageName: String, permName: String, user: UserHandle
        ): Int = permissionManager.getPermissionFlags(packageName, permName, user)

        override fun updatePermissionFlags(
            packageName: String, permName: String, user: UserHandle, flagMask: Int, flagValues: Int
        ): Boolean = permissionManager.updatePermissionFlags(packageName, permName, user, flagMask, flagValues)

        override fun getPackageUid(packageName: String, userId: Int): Int =
            permissionManager.getPackageUid(packageName, userId)

        override fun getUserHandle(userId: Int): UserHandle? =
            permissionManager.getUserHandle(userId)

        override fun setOpsMode(code: Int, uid: Int, packageName: String, mode: Int): Boolean =
            permissionManager.setOpsMode(code, uid, packageName, mode)

        override fun resetAppOps(userId: Int, packageName: String?): Boolean =
            permissionManager.resetAppOps(userId, packageName)

        // ==================== SSAID 管理 ====================

        override fun getPackageSsaidAsUser(packageName: String, uid: Int, userId: Int): String? =
            ssaidManager.getPackageSsaidAsUser(packageName, uid, userId)

        override fun setPackageSsaidAsUser(
            packageName: String, uid: Int, userId: Int, ssaid: String
        ): Boolean = ssaidManager.setPackageSsaidAsUser(packageName, uid, userId, ssaid)

        // ==================== 文件系统 ====================

        override fun readStatFs(path: String): StatFsResult = fileSystem.readStatFs(path)

        override fun readText(path: String): ParcelFileDescriptor = fileSystem.readText(path)

        override fun writeText(path: String, pfd: ParcelFileDescriptor): Boolean =
            fileSystem.writeText(path, pfd)

        override fun calculateTreeSize(path: String): Long = fileSystem.calculateTreeSize(path)

        override fun callTarCli(
            pipeFile: String?, stdOut: String, stdErr: String, argv: Array<String>
        ): Int = fileSystem.callTarCli(pipeFile, stdOut, stdErr, argv)

        override fun mkdirs(path: String): Boolean = fileSystem.mkdirs(path)

        override fun exists(path: String): Boolean = fileSystem.exists(path)

        override fun fileType(path: String): Int = fileSystem.fileType(path)

        override fun deleteRecursively(path: String): Boolean = fileSystem.deleteRecursively(path)

        override fun copyRecursively(source: String, target: String, overwrite: Boolean): Boolean =
            fileSystem.copyRecursively(source, target, overwrite)

        override fun getLastModifiedTime(path: String): Long = fileSystem.getLastModifiedTime(path)

        override fun setLastModifiedTime(path: String, time: Long): Boolean =
            fileSystem.setLastModifiedTime(path, time)

        override fun getUid(path: String): Int = fileSystem.getUid(path)

        override fun setUid(path: String, uid: Int): Boolean = fileSystem.setUid(path, uid)

        override fun getGid(path: String): Int = fileSystem.getGid(path)

        override fun setGid(path: String, gid: Int): Boolean = fileSystem.setGid(path, gid)

        override fun openFile(path: String, mode: Int): ParcelFileDescriptor? =
            fileSystem.openFile(path, mode)

        override fun md5(file: String): String? = fileSystem.md5(file)

        override fun extractTar(tarFifo: String, targetDir: String): Boolean =
            fileSystem.extractTar(tarFifo, targetDir)
    }
}

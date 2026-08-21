package tiiehenry.android.app.snapshot.main.launch.batch

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.os.UserHandle
import tiiehenry.android.snapshot.app.AppPermission
import tiiehenry.android.snapshot.app.IAppManager
import tiiehenry.android.snapshot.app.UserInfoHide
import tiiehenry.android.snapshot.file.IFileCompressor
import tiiehenry.android.snapshot.file.IFileSystem

internal object BatchTestStubFileSystem : IFileSystem {
    override fun fileType(path: String) = 0
    override fun listDir(path: String) = mutableListOf<String>()
    override fun calculateSize(path: String) = 0L
    override fun mkdirs(path: String) = true
    override fun delete(path: String) = true
    override fun exists(path: String) = false
    override fun getParent(path: String) = ""
    override fun length(path: String) = 0L
    override fun getLastModifiedTime(path: String) = 0L
    override fun setLastModifiedTime(path: String, time: Long) = true
    override fun md5(file: String) = ""
    override fun getUid(path: String) = 0
    override fun setUid(path: String, uid: Int) = true
    override fun getGid(path: String) = 0
    override fun setGid(path: String, gid: Int) = true
    override fun openFile(path: String, mode: Int) = null
    override fun openInputStream(path: String) = null
    override fun openOutputStream(path: String) = null
    override fun createTempFile(prefix: String, suffix: String) = ""
    override fun createTarArchive(sourceDir: String, targetFile: String, excludes: MutableList<String>, excludeFiles: MutableList<String>, stdErr: String, stdOut: String) {}
    override fun createTarArchiveForMultiple(files: MutableList<String>, targetFile: String, stdErr: String, stdOut: String) {}
    override fun getCompressor(): IFileCompressor? = null
    override fun mkfifo(path: String, mode: Int) = false
    override fun isFifo(path: String) = false
    override fun extractTar(tarFifo: String, targetDir: String) = false
    override fun cleanDir(path: String) = false
    override fun move(sourcePath: String, targetPath: String) = false
    override fun copyRecursively(source: String, target: String, overwrite: Boolean) = false
}

internal object BatchTestStubAppManager : IAppManager {
    override fun getUsers(): MutableList<UserInfoHide> = mutableListOf()
    override fun getInstalledPackages(flags: Int, userId: Int): MutableList<String> = mutableListOf()
    override fun getPackageInfo(packageName: String, flags: Int, userId: Int): PackageInfo? = null
    override fun getApplicationInfo(packageName: String, flags: Int, userId: Int): ApplicationInfo? = null
    override fun loadLabel(packageName: String, userId: Int): String = packageName
    override fun loadIcon(packageName: String, userId: Int): Bitmap? = null
    override fun getDir(packageName: String, userId: Int, type: Int): String = ""
    override fun isInstalled(packageName: String, userId: Int) = false
    override fun installApk(file: String, userId: Int) = false
    override fun installApks(files: MutableList<String>, userId: Int) = false
    override fun uninstallApk(packageName: String, userId: Int) = false
    override fun forceStopPackage(packageName: String, userId: Int) = true
    override fun clearAppData(packageName: String, userId: Int) {}
    override fun suspendPackage(packageName: String, userId: Int) = true
    override fun unsuspendPackage(packageName: String, userId: Int) = true
    override fun isPackageRunning(packageName: String, userId: Int) = false
    override fun launchApp(packageName: String, userId: Int) = false
    override fun getPermissions(packageName: String, userId: Int): MutableList<AppPermission> = mutableListOf()
    override fun setAppPermission(packageName: String, userId: Int, permission: AppPermission) {}
    override fun setAppPermissions(packageName: String, userId: Int, permissions: MutableList<AppPermission>) {}
    override fun grantRuntimePermission(packageName: String, permName: String, user: UserHandle) {}
    override fun revokeRuntimePermission(packageName: String, permName: String, user: UserHandle) {}
    override fun getPermissionFlags(packageName: String, permName: String, user: UserHandle) = 0
    override fun updatePermissionFlags(packageName: String, permName: String, user: UserHandle, flagMask: Int, flagValues: Int) {}
    override fun getPackageUid(packageName: String, userId: Int) = 0
    override fun getUserHandle(userId: Int): UserHandle? = null
    override fun setOpsMode(code: Int, uid: Int, packageName: String, mode: Int) {}
    override fun resetAppOps(userId: Int, packageName: String) {}
    override fun getPackageSsaidAsUser(packageName: String, uid: Int, userId: Int) = ""
    override fun setPackageSsaidAsUser(packageName: String, userId: Int, ssaid: String) {}
}

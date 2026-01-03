package tiieherny.android.app.snapshotor.app

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import tiiehenry.android.shapshotor.app.AppPermission
import tiiehenry.android.shapshotor.app.IAppManager
import tiiehenry.android.shapshotor.file.IFileSystem
import tiiehenry.android.shapshotor.fs.IFileType
import tiieherny.android.app.snapshotor.SnapShotApp

data class AppInfo(
    val fs: IFileSystem,
    val appManager: IAppManager,
    val packageName: String,
    val userId: Int = 0,
    val versionName: String? = null,
    val versionCode: Long = 0,
) {
    var packageInfo: PackageInfo? = null

    companion object {
        fun from(packageInfo: PackageInfo): AppInfo {
            return AppInfo(
                SnapShotApp.getInstance().fileSystem,
                SnapShotApp.getInstance().appManager,
                packageInfo.packageName,
                (packageInfo.applicationInfo!!.uid) / 100000,
                packageInfo.versionName,
                packageInfo.longVersionCode
            ).apply {
                this.packageInfo = packageInfo
            }
        }
    }

    var archiveIconFile: String? = null
    val icon: Bitmap by lazy {
        loadIcon(appManager) ?: archiveIconFile?.let { loadArchiveIcon(fs, it) }
        ?: BitmapFactory.decodeResource(
            SnapShotApp.getContext().resources,
            android.R.drawable.sym_def_app_icon
        )
    }

    fun loadArchiveIcon(fs: IFileSystem, path: String): Bitmap? {
        if (fs.fileType(path) != IFileType.TYPE_FILE) {
            return null
        }
        val fileDescriptor = fs.openFile(path, ParcelFileDescriptor.MODE_READ_ONLY)
        fileDescriptor.use {
            try {
                return BitmapFactory.decodeFileDescriptor(fileDescriptor.fileDescriptor)
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
    }

    fun loadIcon(appManager: IAppManager): Bitmap? {
        try {
            return appManager.loadIcon(packageName, userId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    val label: String by lazy {
        loadLabel(appManager) ?: packageName
    }

    fun loadLabel(appManager: IAppManager): String? {
        return appManager.loadLabel(packageName, userId)
    }

    fun getApplicationInfo(appManager: IAppManager): ApplicationInfo? {
        return appManager.getApplicationInfo(packageName, 0, userId)
    }

    fun getPackageInfo(appManager: IAppManager): PackageInfo? {
        return appManager.getPackageInfo(packageName, 0, userId)
    }

    fun getPermissions(appManager: IAppManager): List<AppPermission> {
        return appManager.getPermissions(packageName, userId) ?: emptyList()
    }

    fun isSystemApp(appManager: IAppManager): Boolean {
        val appInfo = getApplicationInfo(appManager) ?: return false
        return (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }
}

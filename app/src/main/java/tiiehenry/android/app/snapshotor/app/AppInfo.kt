package tiiehenry.android.app.snapshotor.app

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.ParcelFileDescriptor
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.createBitmap
import tiiehenry.android.app.snapshotor.SnapShotApp
import tiiehenry.android.app.snapshotor.util.PathHelper
import tiiehenry.android.snapshotor.app.AppPermission
import tiiehenry.android.snapshotor.app.IAppManager
import tiiehenry.android.snapshotor.file.IFileSystem
import tiiehenry.android.snapshotor.fs.IFileType

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
        android.util.Log.d(
            "AppInfo",
            "Loading icon for $packageName, archiveIconFile: $archiveIconFile"
        )
        loadIcon(appManager)?.also {
            android.util.Log.d("AppInfo", "Loaded system icon for $packageName")
        } ?: archiveIconFile?.let {
            android.util.Log.d("AppInfo", "Trying to load archive icon from: $it")
            loadArchiveIcon(fs, it)
        }?.also {
            android.util.Log.d("AppInfo", "Loaded archive icon for $packageName from: $it")
        } ?: drawableToBitmap(
            AppCompatResources.getDrawable(
                SnapShotApp.getContext(),
                android.R.drawable.sym_def_app_icon
            )!!
        ).also {
            android.util.Log.w("AppInfo", "Using default icon for $packageName")
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
        if (appManager.isInstalled(packageName, userId)) {
            return appManager.loadLabel(packageName, userId)
        } else return null
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

    /**
     * 获取应用的用户数据目录 (/data/user/[userId]/[packageName])
     */
    fun getUserDir(): String {
        return PathHelper.getAppUserDir(userId, packageName)
    }

    /**
     * 获取应用的用户DE数据目录 (/data/user_de/[userId]/[packageName])
     */
    fun getUserDeDir(): String {
        return PathHelper.getAppUserDeDir(userId, packageName)
    }

    /**
     * 获取应用的数据目录 (/sdcard/Android/data/[packageName])
     */
    fun getDataDir(): String {
        return PathHelper.getAppDataDir(userId, packageName)
    }

    /**
     * 获取应用的OBB目录 (/sdcard/Android/obb/[packageName])
     */
    fun getObbDir(): String {
        return PathHelper.getAppObbDir(userId, packageName)
    }

    /**
     * 获取应用的外部数据目录
     * 目前返回数据目录，可根据需要扩展
     */
    fun getExternalDataDir(): String {
        return getDataDir()
    }
}

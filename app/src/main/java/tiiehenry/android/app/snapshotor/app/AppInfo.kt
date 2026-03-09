package tiiehenry.android.app.snapshotor.app

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.createBitmap
import tiiehenry.android.app.snapshotor.SnapShotApp
import tiiehenry.android.snapshotor.app.AppPermission
import tiiehenry.android.snapshotor.app.IAppManager
import tiiehenry.android.snapshotor.file.IFileSystem
import tiiehenry.android.snapshotor.fs.IFileType
import java.io.IOException
import java.util.zip.ZipFile

data class AppInfo(
    val fs: IFileSystem,
    val appManager: IAppManager,
    val packageName: String,
    val userId: Int = 0,
    val versionName: String? = null,
    val versionCode: Long = 0,
) {
    var archiveLabel: String? = null
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
        Log.d(
            "AppInfo",
            "Loading icon for $packageName, archiveIconFile: $archiveIconFile"
        )
        loadIcon(appManager)?.also {
            Log.d("AppInfo", "Loaded system icon for $packageName")
        } ?: archiveIconFile?.let {
            Log.d("AppInfo", "Trying to load archive icon from: $it")
            loadArchiveIcon(fs, it)
        }?.also {
            Log.d("AppInfo", "Loaded archive icon for $packageName from: $it")
        } ?: drawableToBitmap(
            AppCompatResources.getDrawable(
                SnapShotApp.getContext(),
                android.R.drawable.sym_def_app_icon
            )!!
        ).also {
            Log.w("AppInfo", "Using default icon for $packageName")
        }
    }


    val label: String by lazy {
        loadLabel(appManager) ?: archiveLabel ?: packageName
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

    fun loadLabel(appManager: IAppManager): String? {
        if (appManager.isInstalled(packageName, userId)) {
            return appManager.loadLabel(packageName, userId)
        } else return null
    }

    fun getApplicationInfo(appManager: IAppManager): ApplicationInfo? {
        return appManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA, userId)
    }

    fun getPackageInfo(appManager: IAppManager): PackageInfo? {
        return appManager.getPackageInfo(packageName, PackageManager.GET_META_DATA, userId)
    }

    fun getPermissions(appManager: IAppManager): List<AppPermission> {
        return appManager.getPermissions(packageName, userId) ?: emptyList()
    }

    val isSystemApp: Boolean by lazy {
        val appInfo = getApplicationInfo(appManager)
        appInfo != null && (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }

    /**
     * 检查应用是否是Xposed模块
     */
    val isXposedModule: Boolean by lazy {
        val applicationInfo = getApplicationInfo(appManager) ?: return@lazy false
        try {
            val metaData = applicationInfo.metaData
            (metaData?.containsKey("xposedminversion") == true)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } || isModernModules(applicationInfo)
    }

    private fun isModernModules(info: ApplicationInfo): Boolean {
        val apks = mutableListOf<String>()
        apks.add(info.sourceDir)
        apks.addAll(info.splitSourceDirs?.toMutableList() ?: emptyList())
        for (apk in apks) {
            try {
                ZipFile(apk).use { zip ->
                    if (zip.getEntry("META-INF/xposed/java_init.list") != null) {
                        return true
                    }
                }
            } catch (ignored: IOException) {
            }
        }
        return false
    }

    /**
     * 获取应用的用户数据目录 (/data/user/[userId]/[packageName])
     */
    fun getUserDir(): String {
        return getPackageUserDir(userId)
    }

    /**
     * 获取应用的用户DE数据目录 (/data/user_de/[userId]/[packageName])
     */
    fun getUserDeDir(): String {
        return getPackageUserDeDir(userId)
    }

    /**
     * 获取应用的OBB目录 (/sdcard/Android/obb/[packageName])
     */
    fun getPackageObbDir(): String {
        return getPackageObbDir(userId)
    }

    fun getDataMediaDir(): String = "/data/media"

    fun getPackageMediaDir(): String {
        return "${getDataMediaDir()}/${userId}/Android/media/$packageName"
    }

    fun getPackageDataDir(): String = getPackageDataDir(userId)

    fun getPackageUserDir(userId: Int): String = "/data/user/${userId}/$packageName"
    fun getPackageUserDeDir(userId: Int): String = "/data/user_de/${userId}/$packageName"
    fun getPackageDataDir(userId: Int): String =
        "${getDataMediaDir()}/${userId}/Android/data/$packageName"

    fun getPackageObbDir(userId: Int): String =
        "${getDataMediaDir()}/${userId}/Android/obb/$packageName"

}

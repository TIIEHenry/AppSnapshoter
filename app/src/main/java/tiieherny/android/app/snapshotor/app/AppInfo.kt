package tiieherny.android.app.snapshotor.app

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import tiiehenry.android.shapshotor.app.AppPermission
import tiiehenry.android.shapshotor.app.IAppManager
import tiieherny.android.app.snapshotor.SnapShotApp

data class AppInfo(
    val appManager: IAppManager,
    val packageName: String,
    val userId: Int = 0,
    val versionName: String? = null,
    val versionCode: Long = 0,
) {
    val icon: Bitmap by lazy {
        loadIcon(appManager) ?: BitmapFactory.decodeResource(
            SnapShotApp.getContext().resources,
            android.R.drawable.sym_def_app_icon
        )
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
        return appManager.getPermissions(packageName, userId)?:emptyList()
    }
}

package tiiehenry.android.shapshotor.provider.appmanager

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import tiiehenry.android.shapshotor.app.AppPermission
import tiiehenry.android.shapshotor.provider.AppManagerProvider
import tiiehenry.android.shapshotor.app.IAppManager

class AppManagerProviderImpl(
    hostContext: Context,
    pluginContext: Context
) : AppManagerProvider(hostContext, pluginContext) {

    override fun provide(): IAppManager {
        return AppManagerImpl(hostContext)
    }

    private class AppManagerImpl(private val context: Context) : IAppManager.Stub() {

        private val packageManager: PackageManager = context.packageManager

        override fun getInstalledPackages(flags: Int, userId: Int): List<String> {
            return try {
                packageManager.getInstalledPackages(flags).map { it.packageName }
            } catch (e: Exception) {
                emptyList()
            }
        }

        override fun getPackageInfo(
            packageName: String?,
            flags: Int,
            userId: Int
        ): PackageInfo? {
            if (packageName == null) return null
            return try {
                packageManager.getPackageInfo(packageName, flags)
            } catch (e: Exception) {
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
                packageManager.getApplicationInfo(packageName, flags)
            } catch (e: Exception) {
                null
            }
        }

        override fun loadLabel(packageName: String?, userId: Int): String? {
            if (packageName == null) return null
            return try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                null
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
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                when (type) {
                    DIR_TYPE_DATA -> appInfo.dataDir
                    DIR_TYPE_SOURCE -> appInfo.sourceDir
                    DIR_TYPE_NATIVE -> appInfo.nativeLibraryDir
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }

        override fun getPermissions(
            packageName: String?,
            userId: Int
        ): List<AppPermission?>? {
            TODO("Not yet implemented")
        }

        override fun setAppPermission(packageName: String?, userId: Int, permission: AppPermission?) {
            if (packageName == null || permission == null) return
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager?
                    
                    // Check if we have device admin rights
                    if (dpm != null && isDeviceOwnerOrProfileOwner(dpm)) {
                        // Use DevicePolicyManager to set permission grant state
                        val grantState = if (permission.isGranted()) {
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
                } else {
                    // On older Android versions, permissions are granted at install time
                    // We can't programmatically change them
                    throw SecurityException("Cannot change runtime permissions on Android versions below 6.0")
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

        override fun setAppPermissions(packageName: String?, userId: Int, permissions: MutableList<AppPermission>?) {
            if (packageName == null || permissions == null) return
            
            // Apply each permission individually
            for (permission in permissions) {
                setAppPermission(packageName, userId, permission)
            }
        }

        private fun isDeviceOwnerOrProfileOwner(dpm: DevicePolicyManager): Boolean {
            return try {
                dpm.isDeviceOwnerApp(context.packageName) || dpm.isProfileOwnerApp(context.packageName)
            } catch (e: Exception) {
                false
            }
        }

        override fun isInstalled(packageName: String?, userId: Int): Boolean {
            if (packageName == null) return false
            return try {
                packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: Exception) {
                false
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

        companion object {
            const val DIR_TYPE_DATA = 0
            const val DIR_TYPE_SOURCE = 1
            const val DIR_TYPE_NATIVE = 2
        }
    }
}
package tiiehenry.android.snapshot.provider.service.handler

import android.app.AppOpsManager
import android.app.AppOpsManagerHidden
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManagerHidden
import android.content.pm.PermissionInfo
import android.os.RemoteException
import android.os.UserHandle
import android.os.UserHandleHidden
import androidx.core.content.pm.PermissionInfoCompat
import com.topjohnwu.superuser.ShellUtils
import nota.lang.reflect.ReflectionCache
import tiiehenry.android.snapshot.app.AppPermission
import tiiehenry.android.snapshot.provider.appmanager.util.LogHelper

class PermissionManagementHandler(
    private val context: Context,
    private val mPackageManager: PackageManager,
    private val mPackageManagerHidden: PackageManagerHidden,
    private val mAppOpsManager: AppOpsManager,
    private val mAppOpsManagerHidden: AppOpsManagerHidden
) {

    fun getPermissions(packageName: String, userId: Int): List<AppPermission> {
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

    fun setAppPermission(
        packageName: String,
        userId: Int,
        permission: AppPermission,
        getUserHandle: (Int) -> UserHandle?,
        grantRuntimePermission: (String, String, UserHandle) -> Int,
        revokeRuntimePermission: (String, String, UserHandle?) -> Int,
        getPackageUid: (String, Int) -> Int,
        setOpsMode: (Int, Int, String, Int) -> Boolean
    ): Boolean {
        val userHandle = getUserHandle(userId) ?: run {
            LogHelper.e(
                "SnapshotRootService",
                "setAppPermission",
                "Failed to get user handle for userId: $userId"
            )
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
            LogHelper.i(
                "SnapshotRootService",
                "setAppPermission",
                "Successfully set permission for $packageName"
            )
            true
        } catch (e: SecurityException) {
            LogHelper.e(
                "SnapshotRootService",
                "setAppPermission",
                "Security exception: ${e.message}"
            )
            false
        } catch (e: Exception) {
            LogHelper.e("SnapshotRootService", "setAppPermission", "Error: ${e.message}")
            false
        }
    }

    fun setAppPermissions(
        packageName: String,
        userId: Int,
        permissions: List<AppPermission>,
        setAppPermission: (String, Int, AppPermission) -> Boolean
    ): Boolean {
        return try {
            for (permission in permissions) {
                if (!setAppPermission(packageName, userId, permission)) {
                    LogHelper.e(
                        "SnapshotRootService",
                        "setAppPermissions",
                        "Failed to set permission ${permission.name} for $packageName"
                    )
                    return false
                }
            }
            LogHelper.i(
                "SnapshotRootService",
                "setAppPermissions",
                "Successfully set all permissions for $packageName"
            )
            true
        } catch (e: Exception) {
            LogHelper.e("SnapshotRootService", "setAppPermissions", "Error: ${e.message}")
            false
        }
    }

    fun grantRuntimePermission(
        packageName: String,
        permName: String,
        user: UserHandle
    ): Int {
        return try {
            mPackageManagerHidden.grantRuntimePermission(packageName, permName, user)
            LogHelper.i(
                "SnapshotRootService",
                "grantRuntimePermission",
                "Successfully granted permission: $permName for $packageName"
            )
            1 // 成功
        } catch (e: SecurityException) {
            if (e.message?.contains("is not a changeable permission type") == true) {
                LogHelper.w(
                    "SnapshotRootService",
                    "grantRuntimePermission",
                    "not a changeable permission type: $permName for $packageName"
                )
                -1 // fixed permission type
            } else {
                LogHelper.e(
                    "SnapshotRootService",
                    "grantRuntimePermission",
                    "Failed to grant permission: $permName for $packageName: ${e.message}"
                )
                0 // 失败
            }
        } catch (e: Exception) {
            LogHelper.e(
                "SnapshotRootService",
                "grantRuntimePermission",
                "Failed to grant permission: $permName for $packageName: ${e.message}"
            )
            0 // 失败
        }
    }

    fun revokeRuntimePermission(
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
            LogHelper.i(
                "SnapshotRootService",
                "revokeRuntimePermission",
                "Successfully revoked permission: $permName for $packageName"
            )
            1 // 成功
        } catch (e: SecurityException) {
            if (e.message?.contains("is not a changeable permission type") == true) {
                LogHelper.w(
                    "SnapshotRootService",
                    "revokeRuntimePermission",
                    "not a changeable permission type: $permName for $packageName"
                )
                -1 // fixed permission type
            } else {
                LogHelper.e(
                    "SnapshotRootService",
                    "revokeRuntimePermission",
                    "Failed to revoke permission: $permName for $packageName: ${e.message}"
                )
                0 // 失败
            }
        } catch (e: Exception) {
            LogHelper.e(
                "SnapshotRootService",
                "revokeRuntimePermission",
                "Failed to revoke permission: $permName for $packageName: ${e.message}"
            )
            0 // 失败
        }
    }

    fun getPermissionFlags(
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

    fun updatePermissionFlags(
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
            LogHelper.i(
                "SnapshotRootService",
                "updatePermissionFlags",
                "Successfully updated permission flags for $permName in $packageName"
            )
            true
        } catch (e: Exception) {
            LogHelper.e(
                "SnapshotRootService",
                "updatePermissionFlags",
                "Failed to update permission flags: $permName for $packageName: ${e.message}"
            )
            false
        }
    }

    fun getPackageUid(packageName: String, userId: Int): Int {
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

    fun getUserHandle(userId: Int): UserHandle? {
        return try {
            UserHandleHidden.of(userId)
        } catch (e: Exception) {
            throw RemoteException("Failed to get user handle for: $userId: ${e.message}")
        }
    }

    fun setOpsMode(code: Int, uid: Int, packageName: String, mode: Int): Boolean {
        return try {
            mAppOpsManagerHidden.setMode(code, uid, packageName, mode)
            LogHelper.i(
                "SnapshotRootService",
                "setOpsMode",
                "Successfully set ops mode for $packageName"
            )
            true
        } catch (e: Exception) {
            LogHelper.e(
                "SnapshotRootService",
                "setOpsMode",
                "Failed to set ops mode for $packageName: ${e.message}"
            )
            false
        }
    }

    fun resetAppOps(userId: Int, packageName: String?): Boolean {
        if (packageName == null) {
            LogHelper.e("SnapshotRootService", "resetAppOps", "Package name is null")
            return false
        }
        return try {
            ShellUtils.fastCmd("appops reset --user $userId $packageName")
            LogHelper.i(
                "SnapshotRootService",
                "resetAppOps",
                "Successfully reset appops for $packageName"
            )
            true
        } catch (e: Exception) {
            LogHelper.e(
                "SnapshotRootService",
                "resetAppOps",
                "Failed to reset appops for $packageName: ${e.message}"
            )
            false
        }
    }
}

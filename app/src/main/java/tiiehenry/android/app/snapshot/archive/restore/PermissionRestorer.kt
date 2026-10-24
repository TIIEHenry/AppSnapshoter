package tiiehenry.android.app.snapshot.archive.restore

import android.app.AppOpsManagerHidden
import android.os.RemoteException
import android.util.Log
import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.archive.MetaInfoHelper
import tiiehenry.android.snapshot.app.IAppManager
import tiiehenry.android.snapshot.file.IFileSystem
import java.io.File

/**
 * 权限恢复器 - 负责恢复应用权限、AppOps 和 SSAID
 */
object PermissionRestorer {

    private const val TAG = "PermissionRestorer"

    suspend fun restorePermissions(
        loadingDialog: tiiehenry.android.app.snapshot.main.launch.makearchive.progress.IItemProgressDialog,
        archiveItem: ArchiveItem,
        fs: IFileSystem,
        appManager: IAppManager,
        packageName: String,
        userId: Int
    ) {
        val fixedPermissionsFile =
            File(SnapshotApp.getInstance().globalRootPath, "fixed_permissions.json")
        val fixedPermissions = if (fixedPermissionsFile.exists()) {
            try {
                val jsonStr = fixedPermissionsFile.readText()
                JSON.parseArray(jsonStr, String::class.java) ?: emptyList<String>()
            } catch (e: Exception) {
                Log.e(TAG, "读取 fixed_permissions.json 失败：${e.message}")
                emptyList<String>()
            }
        } else {
            emptyList<String>()
        }.toMutableList()
        Log.i(TAG, "已加载 ${fixedPermissions.size} 个不可变更的权限")

        val app = SnapshotApp.getInstance()
        withContext(Dispatchers.Main) {
            loadingDialog.setItemMessage(app.getString(R.string.archive_restoring_permissions))
            loadingDialog.setItemStatus(app.getString(R.string.ellipsis))
        }
        val permissionsFile = "${archiveItem.path}/${MetaInfoHelper.PERMISSIONS_FILE_NAME}"
        val metaPermissions = MetaInfoHelper.readPermissions(fs, permissionsFile)
        val newFixedPermissions = mutableListOf<String>()
        if (metaPermissions.isNotEmpty()) {
            Log.i(TAG, "Permissions size: ${metaPermissions.size}...")
            val uid = appManager.getPackageUid(packageName, userId)
            val user = appManager.getUserHandle(userId)
            newFixedPermissions += writeArchivedPermissions(
                permissions = metaPermissions.map { metaPermission ->
                    ArchivedPermission(
                        metaPermission.name,
                        metaPermission.isGranted,
                        metaPermission.op,
                        metaPermission.mode,
                    )
                },
                fixedNames = fixedPermissions,
                uid = uid,
                userPresent = user != null,
                packageName = packageName,
                resetAppOps = { appManager.resetAppOps(userId, packageName) },
                setOpsMode = { op, mode -> appManager.setOpsMode(op, uid, packageName, mode) },
                grant = { name -> appManager.grantRuntimePermission(packageName, name, user!!) },
                revoke = { name -> appManager.revokeRuntimePermission(packageName, name, user!!) },
            )
            newFixedPermissions.forEach { name ->
                Log.w(TAG, "权限 $name 是不可变更的类型，添加到固定列表")
            }
            Log.i(TAG, "已恢复 ${metaPermissions.size} 个权限")
        } else {
            Log.i(TAG, "未找到权限数据或权限列表为空")
        }

        if (archiveItem.metaInfo.ssaid.isNotEmpty()) {
            appManager.setPackageSsaidAsUser(packageName, userId, archiveItem.metaInfo.ssaid)
        }

        if (newFixedPermissions.isNotEmpty()) {
            try {
                val str = JSON.toJSONString(fixedPermissions, JSONWriter.Feature.PrettyFormat)
                fixedPermissionsFile.writeText(str)
                Log.i(TAG, "已保存 ${fixedPermissions.size} 个不可变更的权限")
            } catch (e: Exception) {
                Log.e(TAG, "保存 fixed_permissions.json 失败：${e.message}")
            }
        }
    }
}

internal data class ArchivedPermission(
    val name: String,
    val granted: Boolean,
    val op: Int,
    val mode: Int,
)

internal fun isUnchangeablePermissionType(isRemoteException: Boolean, message: String?): Boolean =
    isRemoteException && message?.contains("not a changeable permission type") == true

/**
 * After [resetAppOps], every archived op other than [opNone] is written back.
 * Names already in [fixedNames] skip runtime grant/revoke only.
 * [setOpsMode] and ordinary grant/revoke failures propagate.
 * A remote "not a changeable permission type" grant is recorded into [fixedNames].
 */
internal fun writeArchivedPermissions(
    permissions: List<ArchivedPermission>,
    fixedNames: MutableList<String>,
    uid: Int,
    userPresent: Boolean,
    packageName: String,
    resetAppOps: () -> Boolean,
    setOpsMode: (op: Int, mode: Int) -> Boolean,
    grant: (name: String) -> Unit,
    revoke: (name: String) -> Unit,
    isUnchangeable: (Throwable) -> Boolean = { error ->
        isUnchangeablePermissionType(error is RemoteException, error.message)
    },
    opNone: Int = AppOpsManagerHidden.OP_NONE,
): List<String> {
    if (permissions.isEmpty()) return emptyList()
    if (uid == -1 || !userPresent) {
        throw IllegalStateException("Failed to get uid or user handle for $packageName")
    }
    if (!resetAppOps()) {
        throw IllegalStateException("Failed to reset app ops for $packageName")
    }
    val recorded = mutableListOf<String>()
    for (permission in permissions) {
        if (permission.op != opNone) {
            if (!setOpsMode(permission.op, permission.mode)) {
                throw IllegalStateException(
                    "Failed to set app op ${permission.op} for $packageName",
                )
            }
        }
        if (permission.name in fixedNames) continue
        try {
            if (permission.granted) grant(permission.name) else revoke(permission.name)
        } catch (error: Exception) {
            if (isUnchangeable(error)) {
                fixedNames.add(permission.name)
                recorded.add(permission.name)
            } else {
                throw error
            }
        }
    }
    return recorded
}

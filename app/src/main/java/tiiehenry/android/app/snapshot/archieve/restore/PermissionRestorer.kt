package tiiehenry.android.app.snapshot.archieve.restore

import android.app.AppOpsManagerHidden
import android.util.Log
import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.archieve.MetaInfoHelper
import tiiehenry.android.snapshot.app.AppPermission
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

        withContext(Dispatchers.Main) {
            loadingDialog.setItemMessage("正在恢复权限")
            loadingDialog.setItemStatus("...")
        }
        val permissionsFile = "${archiveItem.path}/${MetaInfoHelper.PERMISSIONS_FILE_NAME}"
        val metaPermissions = MetaInfoHelper.readPermissions(fs, permissionsFile)
        val newFixedPermissions = mutableListOf<String>()
        if (metaPermissions.isNotEmpty()) {
            val appPermissions = metaPermissions.map { metaPermission ->
                AppPermission(
                    metaPermission.isGranted(),
                    metaPermission.mode,
                    metaPermission.name,
                    metaPermission.op
                )
            }

            val uid = appManager.getPackageUid(packageName, userId)
            val user = appManager.getUserHandle(userId)

            if (uid != -1 && user != null) {
                appManager.resetAppOps(userId, packageName)

                Log.i(TAG, "Permissions size: ${appPermissions.size}...")

                appPermissions.forEach {
                    Log.i(
                        TAG,
                        "Permission name: ${it.name}, isGranted: ${it.isGranted}, op: ${it.op}, mode: ${it.mode}"
                    )

                    val isFixed = fixedPermissions.contains(it.name)
                    if (isFixed) {
                        Log.i(TAG, "跳过不可变更的权限：${it.name}")
                        return@forEach
                    }

                    runCatching {
                        try {
                            if (it.isGranted) {
                                appManager.grantRuntimePermission(packageName, it.name, user)
                            } else {
                                appManager.revokeRuntimePermission(packageName, it.name, user)
                            }
                        } catch (e: android.os.RemoteException) {
                            if (e.message?.contains("not a changeable permission type") == true) {
                                Log.w(TAG, "权限 ${it.name} 是不可变更的类型，添加到固定列表")
                                fixedPermissions.add(it.name)
                                newFixedPermissions.add(it.name)
                            } else {
                                e.printStackTrace()
                            }
                        }
                        if (it.op != AppOpsManagerHidden.OP_NONE) {
                            appManager.setOpsMode(it.op, uid, packageName, it.mode)
                        }
                    }
                }
                Log.i(TAG, "已恢复 ${appPermissions.size} 个权限")
            } else {
                Log.i(TAG, "Failed to get uid or user handle for $packageName")
            }
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

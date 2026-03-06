package tiiehenry.android.snapshotor.provider.appmanager.root

import android.app.ActivityManager
import android.app.ActivityManagerHidden
import android.app.ActivityThread
import android.app.AppOpsManager
import android.app.AppOpsManagerHidden

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManagerHidden
import android.content.pm.PermissionInfo
import android.content.pm.UserInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.UserHandle
import android.os.UserHandleHidden
import android.os.UserManagerHidden
import android.util.Log
import androidx.core.content.pm.PermissionInfoCompat
import com.android.providers.settings.SettingsState
import com.android.providers.settings.SettingsStateApi26
import com.android.providers.settings.SettingsStateApi31
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import com.topjohnwu.superuser.ipc.RootService
import com.xayah.hiddenapi.castTo
import com.xayah.libnative.NativeLib
import nota.lang.reflect.ReflectionCache
import tiiehenry.android.snapshotor.app.AppPermission
import tiiehenry.android.snapshotor.provider.appmanager.model.AppInfo
import tiiehenry.android.snapshotor.provider.appmanager.model.AppStorage
import tiiehenry.android.snapshotor.provider.appmanager.model.Info
import tiiehenry.android.snapshotor.provider.appmanager.model.Storage
import tiiehenry.android.snapshotor.provider.appmanager.parcelables.BytesParcelable
import tiiehenry.android.snapshotor.provider.appmanager.service.IAppManageRootService
import tiiehenry.android.snapshotor.provider.appmanager.util.LogHelper
import tiiehenry.android.snapshotor.provider.appmanager.util.PathHelper
import java.io.File

class AppManageRootService : RootService() {
    override fun onBind(intent: Intent): IBinder = Impl(applicationContext).apply { onBind() }

    private class Impl(private val context: Context) : IAppManageRootService.Stub() {
        private lateinit var mSystemContext: Context
        private lateinit var mPackageManager: PackageManager
        private lateinit var mPackageManagerHidden: PackageManagerHidden
        private lateinit var mUserManager: UserManagerHidden
        private lateinit var mAppOpsManager: AppOpsManager
        private lateinit var mAppOpsManagerHidden: AppOpsManagerHidden
        private lateinit var mActivityManager: ActivityManager
        private lateinit var mActivityManagerHidden: ActivityManagerHidden

        fun onBind() {
            mSystemContext = ActivityThread.systemMain().systemContext
            mPackageManager = mSystemContext.packageManager
            mPackageManagerHidden = mPackageManager.castTo()
            mUserManager = UserManagerHidden.get(mSystemContext).castTo()
            mAppOpsManager = mSystemContext.getSystemService(Context.APP_OPS_SERVICE).castTo()
            mAppOpsManagerHidden = mAppOpsManager.castTo()
            mActivityManager = mSystemContext.getSystemService(Context.ACTIVITY_SERVICE).castTo()
            mActivityManagerHidden = mActivityManager.castTo()
        }

        override fun testConnection() {}

        /**
         * Android 系统核心组件包名列表
         * 这些包在获取已安装应用列表时会被过滤掉
         */
        private val CORE_SYSTEM_PACKAGES = setOf(
            // Android 框架核心
            "android",
            "com.android.providers.settings",
            "com.android.providers.media",
            "com.android.providers.telephony",
            "com.android.providers.contacts",
            "com.android.providers.calendar",
            "com.android.shell",
        )

        override fun getInstalledAppInfos(): ParcelFileDescriptor {
            return writeToParcel(context) { parcel ->
                val infos = mutableListOf<AppInfo>()
                val users = mUserManager.users
                users.forEach { user ->
                    val installedPackagesAsUser =
                        mPackageManagerHidden.getInstalledPackagesAsUser(0, user.id)
                    installedPackagesAsUser.removeIf { it.packageName in CORE_SYSTEM_PACKAGES }
                    infos.addAll(installedPackagesAsUser.map {
                        AppInfo(
                            packageName = it.packageName,
                            userId = user.id,
                            info = Info(
                                uid = it.applicationInfo?.uid ?: 0,
                                label = it.applicationInfo?.loadLabel(mPackageManager).toString(),
                                versionName = it.versionName ?: "",
                                versionCode = it.longVersionCode,
                                flags = it.applicationInfo?.flags ?: 0,
                                firstInstallTime = it.firstInstallTime,
                                lastUpdateTime = it.lastUpdateTime
                            )
                        )
                    })
                }
                parcel.writeTypedList(infos)
            }
        }

        override fun getInstalledAppStorages(): ParcelFileDescriptor {
            return writeToParcel(context) { parcel ->
                val packages = mutableListOf<Pair<Int, PackageInfo>>()
                val storages = mutableListOf<AppStorage>()
                val users = mUserManager.users
                users.forEach { user ->
                    packages.addAll(
                        mPackageManagerHidden.getInstalledPackagesAsUser(0, user.id)
                            .map { user.id to it })
                }
                storages.addAll(packages.map { (userId, item) ->
                    val apkBytes = runCatching {
                        item.applicationInfo?.sourceDir?.let { path -> File(path).parent }
                            ?.let { path -> NativeLib.calculateTreeSize(path) }
                    }.getOrNull() ?: 0
                    val userBytes = NativeLib.calculateTreeSize(
                        PathHelper.getAppUserDir(
                            userId,
                            item.packageName
                        )
                    )
                    val userDeBytes = NativeLib.calculateTreeSize(
                        PathHelper.getAppUserDeDir(
                            userId,
                            item.packageName
                        )
                    )
                    val dataBytes = NativeLib.calculateTreeSize(
                        PathHelper.getAppDataDir(
                            userId,
                            item.packageName
                        )
                    )
                    val obbBytes = NativeLib.calculateTreeSize(
                        PathHelper.getAppObbDir(
                            userId,
                            item.packageName
                        )
                    )
                    val mediaBytes = NativeLib.calculateTreeSize(
                        PathHelper.getAppMediaDir(
                            userId,
                            item.packageName
                        )
                    )
                    AppStorage(
                        packageName = item.packageName,
                        userId = userId,
                        storage = Storage(
                            apkBytes = apkBytes,
                            internalDataBytes = userBytes + userDeBytes,
                            externalDataBytes = dataBytes,
                            additionalDataBytes = obbBytes + mediaBytes,
                        )
                    )
                })
                parcel.writeTypedList(storages)
            }
        }

        override fun getUsers(): List<UserInfo> {
            return mUserManager.users
        }

        override fun getPrivilegedConfiguredNetworks(): List<BytesParcelable> {
            return emptyList()
        }

        override fun addNetworks(configs: List<BytesParcelable>): IntArray {
            return intArrayOf()
        }


        override fun getPackageSourceDir(packageName: String, userId: Int): List<String> {
            val sourceDirList = mutableListOf<String>()
            val packageInfo = mPackageManagerHidden.getPackageInfoAsUser(packageName, 0, userId)
            packageInfo.applicationInfo?.sourceDir?.also { sourceDirList.add(it) }
            val splitSourceDirs = packageInfo.applicationInfo?.splitSourceDirs
            if (!splitSourceDirs.isNullOrEmpty()) for (i in splitSourceDirs) sourceDirList.add(i)
            return sourceDirList
        }

        override fun getPackageInfo(packageName: String?, flags: Int, userId: Int): PackageInfo? {
            if (packageName == null) return null
            return try {
                mPackageManagerHidden.getPackageInfoAsUser(packageName, flags, userId)
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "getPackageInfo",
                    "Failed to get package info: $packageName",
                    e
                )
                null
            }
        }

        override fun getApplicationInfo(
            packageName: String,
            flags: Int,
            userId: Int
        ): ApplicationInfo? {
            return try {
                mPackageManagerHidden.getApplicationInfoAsUser(packageName, flags, userId)
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "getApplicationInfo",
                    "Failed to get application info: $packageName",
                    e
                )
                null
            }
        }

        override fun loadLabel(packageName: String, userId: Int): String? {
            return try {
                val appInfo = mPackageManagerHidden.getApplicationInfoAsUser(packageName, 0, userId)
                appInfo?.loadLabel(mPackageManager)?.toString() ?: packageName
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "loadLabel",
                    "Failed to load label: $packageName",
                    e
                )
                packageName
            }
        }

        override fun loadIcon(packageName: String, userId: Int): Bitmap? {
            return try {
                val appInfo = mPackageManagerHidden.getApplicationInfoAsUser(packageName, 0, userId)
                Log.i("AppManageRootService", "loadIcon: $appInfo")
                val drawable = appInfo?.loadIcon(mPackageManager)
                drawableToBitmap(drawable)
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "loadIcon",
                    "Failed to load icon: $packageName",
                    e
                )
                null
            }
        }

        override fun getPermissions(packageName: String, userId: Int): List<AppPermission> {
            return try {
                val permissions = mutableListOf<AppPermission>()
                val packageInfo = mPackageManagerHidden.getPackageInfoAsUser(
                    packageName,
                    PackageManager.GET_PERMISSIONS,
                    userId
                ) ?: throw Exception("Failed to get package info: $packageName")
                val uid = packageInfo.applicationInfo?.uid
                    ?: throw Exception("Failed to get uid: $packageName")
                val requestedPermissions = packageInfo.requestedPermissions?.toList() ?: listOf()
                val requestedPermissionsFlags =
                    packageInfo.requestedPermissionsFlags?.toList() ?: listOf()
                // 使用反射获取 AppOps 信息
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
                        // 使用 getOps() 方法获取操作列表
                        val opList = reflection.getMethod(pkgOps.javaClass, "getOps")
                            .invoke(pkgOps) as? List<*>
                        opList?.forEach { opEntry ->
                            // 使用 getOp() 和 getMode() 方法获取值
                            val getOpMethod = reflection.getMethod(opEntry!!.javaClass, "getOp")
                            val getModeMethod = reflection.getMethod(opEntry.javaClass, "getMode")
                            val opValue = getOpMethod.invoke(opEntry) as? Int
                            val modeValue = getModeMethod.invoke(opEntry) as? Int
                            if (opValue != null && modeValue != null) {
                                resultMap[opValue] = modeValue
                            } else {
                                LogHelper.w(
                                    "AppManageRootService",
                                    "getPermissions",
                                    "opValue or modeValue is null"
                                )
                            }
                        }
                    }
                    resultMap
                } catch (e: Exception) {
                    LogHelper.w(
                        "AppManageRootService",
                        "getPermissions",
                        "Failed to get ops: ${e.message}"
                    )
                    null
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
                LogHelper.e(
                    "AppManageRootService",
                    "getPermissions",
                    "Failed to get permissions: $packageName",
                    e
                )
                emptyList()
            }
        }

        override fun setAppPermission(
            packageName: String,
            userId: Int,
            permission: AppPermission
        ) {
            try {
                val userHandle = getUserHandle(userId) ?: run {
                    LogHelper.w(
                        "AppManageRootService",
                        "setAppPermission",
                        "Failed to get user handle for userId: $userId"
                    )
                    return
                }

                if (permission.isGranted) {
                    grantRuntimePermission(packageName, permission.name, userHandle)
                } else {
                    revokeRuntimePermission(packageName, permission.name, userHandle)
                }

                if (permission.op != AppOpsManagerHidden.OP_NONE) {
                    val uid = getPackageUid(packageName, userId)
                    setOpsMode(permission.op, uid, packageName, permission.mode)
                }
            } catch (e: SecurityException) {
                LogHelper.e(
                    "AppManageRootService",
                    "setAppPermission",
                    "Security exception: ${e.message}",
                    e
                )
            } catch (e: Exception) {
                LogHelper.e("AppManageRootService", "setAppPermission", "Error: ${e.message}", e)
            }
        }

        override fun setAppPermissions(
            packageName: String,
            userId: Int,
            permissions: List<AppPermission>
        ) {
            // Apply each permission individually
            for (permission in permissions) {
                setAppPermission(packageName, userId, permission)
            }
        }

        override fun isInstalled(packageName: String?, userId: Int): Boolean {
            if (packageName == null) return false
            return try {
                mPackageManagerHidden.getPackageInfoAsUser(packageName, 0, userId)
                true
            } catch (e: Exception) {
                false
            }
        }

        override fun installApk(file: String, userId: Int): Boolean {
            try {
                //                            Pm.install(userId = userId, src = apksPath.first()).also { result ->
                //                                isSuccess = isSuccess && result.isSuccess
                //                                out.addAll(result.out)
                //                            }
                // 使用 pm install 命令安装 APK
                // -r: 允许重新安装已存在的应用
                // -t: 允许安装测试应用
                val installCmd = "pm install -r -t --user $userId \"$file\""

                LogHelper.d(
                    "AppManageRootService",
                    "installApk",
                    "Executing install command: $installCmd"
                )

                // 获取 root shell 并执行安装命令
                val shell = Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(120) // 设置较长的超时时间，因为安装可能需要较长时间
                    .build()

                val result = shell.newJob().to(null, null).add(installCmd).exec()
                shell.close()

                val output = result.out.joinToString("\n")
                val errOutput = result.err.joinToString("\n")

                LogHelper.d(
                    "AppManageRootService",
                    "installApk",
                    "Install output: $output\nError output: $errOutput\nExit code: ${result.code}"
                )

                // 检查安装结果
                return result.isSuccess//todo query package info not null
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "installApk",
                    "Failed to install APK: $file",
                    e
                )
                return false
            }
        }

        override fun installApks(files: List<String>, userId: Int): Boolean {
            if (files.isEmpty()) return false
//                            var pmSession = ""
//                            Pm.Install.create(userId = userId).also { result ->
//                                if (result.isSuccess) pmSession = result.outString
//                            }
//                            if (pmSession.isNotEmpty()) {
//                                out.add(log { "Install session: $pmSession." })
//
//                            } else {
//                                isSuccess = false
//                                out.add(log { "Failed to get install session." })
//                            }
//
//                            apksPath.forEach { apkPath ->
//                                Pm.Install.write(session = pmSession, srcName = PathUtil.getFileName(apkPath), src = apkPath).also { result ->
//                                    isSuccess = isSuccess && result.isSuccess
//                                    out.addAll(result.out)
//                                }
//                            }
//
//                            Pm.Install.commit(pmSession).also { result ->
//                                isSuccess = isSuccess && result.isSuccess
//                                out.addAll(result.out)
//                            }
            try {
                // 获取 root shell
                val shell = Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(300) // 较长的超时时间
                    .build()

                // 1. 创建安装会话
                val createCmd = "pm install-create --user $userId"
                LogHelper.d(
                    "AppManageRootService",
                    "installApks",
                    "Creating install session: $createCmd"
                )

                var createResult = shell.newJob().to(null, null).add(createCmd).exec()
                var output = createResult.out.joinToString("\n")
                var errOutput = createResult.err.joinToString("\n")

                LogHelper.d(
                    "AppManageRootService",
                    "installApks",
                    "Create session output: $output, Error: $errOutput"
                )

                // 从输出中提取session ID，格式如: [20061]
                val sessionMatch = Regex("\\[(\\d+)\\]").find(output)
                if (sessionMatch == null) {
                    LogHelper.e(
                        "AppManageRootService",
                        "installApks",
                        "Failed to get session ID from output: $output"
                    )
                    shell.close()
                    return false
                }
                val sessionId = sessionMatch.groupValues[1]
                LogHelper.d("AppManageRootService", "installApks", "Got session ID: $sessionId")

                // 2. 写入每个 APK 文件
                for ((index, filePath) in files.withIndex()) {
                    // 获取文件大小
                    val file = File(filePath)
                    val fileSize = file.length()

                    // 确定 split 名称：第一个是 base，其余按顺序命名
                    val splitName = if (index == 0) "base" else "split_$index"

                    val writeCmd =
                        "pm install-write -S $fileSize - $sessionId $splitName \"$filePath\""
                    LogHelper.d("AppManageRootService", "installApks", "Writing APK: $writeCmd")

                    createResult = shell.newJob().to(null, null).add(writeCmd).exec()
                    output = createResult.out.joinToString("\n")
                    errOutput = createResult.err.joinToString("\n")

                    LogHelper.d(
                        "AppManageRootService",
                        "installApks",
                        "Write output: $output, Error: $errOutput"
                    )

                    if (!createResult.isSuccess && !output.contains("Success")) {
                        LogHelper.e(
                            "AppManageRootService",
                            "installApks",
                            "Failed to write APK: $filePath"
                        )
                        // 失败时尝试中止会话
                        shell.newJob().to(null, null).add("pm install-abandon $sessionId").exec()
                        shell.close()
                        return false
                    }
                }

                // 3. 提交安装会话
                val commitCmd = "pm install-commit $sessionId"
                LogHelper.d(
                    "AppManageRootService",
                    "installApks",
                    "Committing install session: $commitCmd"
                )

                createResult = shell.newJob().to(null, null).add(commitCmd).exec()
                output = createResult.out.joinToString("\n")
                errOutput = createResult.err.joinToString("\n")

                LogHelper.d(
                    "AppManageRootService",
                    "installApks",
                    "Commit output: $output, Error: $errOutput, Exit code: ${createResult.code}"
                )

                shell.close()

                // 检查安装结果
                return createResult.isSuccess//todo query package info not null
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "installApks",
                    "Failed to install APKs: ${files.joinToString(", ")}",
                    e
                )
                return false
            }
        }

        override fun uninstallApk(packageName: String?, userId: Int): Boolean {
            if (packageName == null) return false

            try {
                // 使用 pm uninstall 命令卸载应用
                val uninstallCmd = "pm uninstall --user $userId $packageName"

                LogHelper.d(
                    "AppManageRootService",
                    "uninstallApk",
                    "Executing uninstall command: $uninstallCmd"
                )

                // 获取 root shell 并执行卸载命令
                val shell = Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(60)
                    .build()

                val result = shell.newJob().to(null, null).add(uninstallCmd).exec()
                shell.close()

                val output = result.out.joinToString("\n")
                val errOutput = result.err.joinToString("\n")

                LogHelper.d(
                    "AppManageRootService",
                    "uninstallApk",
                    "Uninstall output: $output\nError output: $errOutput\nExit code: ${result.code}"
                )

                // 检查卸载结果
                return result.isSuccess && (
                        output.contains("Success") ||
                                output.contains("success") ||
                                output.contains("not installed") // 如果应用未安装，也视为成功
                        )
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "uninstallApk",
                    "Failed to uninstall APK: $packageName",
                    e
                )
                return false
            }
        }

        override fun forceStopPackage(packageName: String?, userId: Int) {
            if (packageName == null) return
            try {
                val cmd = "am force-stop --user $userId $packageName"
                LogHelper.d(
                    "AppManageRootService",
                    "forceStopPackage",
                    "Executing force-stop command: $cmd"
                )
                val shell = Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(30)
                    .build()
                val result = shell.newJob().to(null, null).add(cmd).exec()
                shell.close()
                LogHelper.d(
                    "AppManageRootService",
                    "forceStopPackage",
                    "Force-stop output: ${result.out.joinToString("\n")}\nExit code: ${result.code}"
                )
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "forceStopPackage",
                    "Failed to force-stop package: $packageName",
                    e
                )
            }
        }

        override fun clearAppData(packageName: String?, userId: Int) {
            if (packageName == null) return
            try {
                val cmd = "pm clear --user $userId $packageName"
                LogHelper.d(
                    "AppManageRootService",
                    "clearAppData",
                    "Executing clear data command: $cmd"
                )
                val shell = Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(60)
                    .build()
                val result = shell.newJob().to(null, null).add(cmd).exec()
                shell.close()
                val output = result.out.joinToString("\n")
                LogHelper.d(
                    "AppManageRootService",
                    "clearAppData",
                    "Clear data output: $output\nExit code: ${result.code}"
                )
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "clearAppData",
                    "Failed to clear app data: $packageName",
                    e
                )
            }
        }

        override fun suspendPackage(packageName: String?, userId: Int) {
            if (packageName == null) return
            try {
                val cmd = "pm suspend $packageName"
                LogHelper.d(
                    "AppManageRootService",
                    "suspendPackage",
                    "Executing suspend command: $cmd"
                )
                val shell = Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(30)
                    .build()
                val result = shell.newJob().to(null, null).add(cmd).exec()
                shell.close()
                LogHelper.d(
                    "AppManageRootService",
                    "suspendPackage",
                    "Suspend output: ${result.out.joinToString("\n")}\nExit code: ${result.code}"
                )
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "suspendPackage",
                    "Failed to suspend package: $packageName",
                    e
                )
            }
        }

        override fun unsuspendPackage(packageName: String?, userId: Int) {
            if (packageName == null) return
            try {
                val cmd = "pm unsuspend $packageName"
                LogHelper.d(
                    "AppManageRootService",
                    "unsuspendPackage",
                    "Executing unsuspend command: $cmd"
                )
                val shell = Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(30)
                    .build()
                val result = shell.newJob().to(null, null).add(cmd).exec()
                shell.close()
                LogHelper.d(
                    "AppManageRootService",
                    "unsuspendPackage",
                    "Unsuspend output: ${result.out.joinToString("\n")}\nExit code: ${result.code}"
                )
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "unsuspendPackage",
                    "Failed to unsuspend package: $packageName",
                    e
                )
            }
        }

        override fun grantRuntimePermission(
            packageName: String,
            permName: String,
            user: UserHandle
        ) {
            try {
                mPackageManagerHidden.grantRuntimePermission(packageName, permName, user)
                LogHelper.d(
                    "AppManageRootService",
                    "grantRuntimePermission",
                    "Granted permission: $permName for $packageName"
                )
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "grantRuntimePermission",
                    "Failed to grant permission: $permName for $packageName",
                    e
                )
            }
        }

        override fun revokeRuntimePermission(
            packageName: String?,
            permName: String?,
            user: UserHandle?
        ) {
            if (packageName == null || permName == null || user == null) return
            try {
                mPackageManagerHidden.revokeRuntimePermission(packageName, permName, user)
                LogHelper.d(
                    "AppManageRootService",
                    "revokeRuntimePermission",
                    "Revoked permission: $permName for $packageName"
                )
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "revokeRuntimePermission",
                    "Failed to revoke permission: $permName for $packageName",
                    e
                )
            }
        }

        override fun getPermissionFlags(
            packageName: String?,
            permName: String?,
            user: UserHandle?
        ): Int {
            if (packageName == null || permName == null || user == null) return 0
            return try {
                mPackageManagerHidden.getPermissionFlags(permName, packageName, user)
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "getPermissionFlags",
                    "Failed to get permission flags: $permName for $packageName",
                    e
                )
                0
            }
        }

        override fun updatePermissionFlags(
            packageName: String?,
            permName: String?,
            user: UserHandle?,
            flagMask: Int,
            flagValues: Int
        ) {
            if (packageName == null || permName == null || user == null) return
            try {
                mPackageManagerHidden.updatePermissionFlags(
                    permName,
                    packageName,
                    flagMask,
                    flagValues,
                    user
                )
                LogHelper.d(
                    "AppManageRootService",
                    "updatePermissionFlags",
                    "Updated permission flags for: $permName in $packageName"
                )
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "updatePermissionFlags",
                    "Failed to update permission flags: $permName for $packageName",
                    e
                )
            }
        }

        override fun getPackageUid(packageName: String?, userId: Int): Int {
            if (packageName == null) return -1
            return try {
                mPackageManagerHidden.getPackageInfoAsUser(
                    packageName,
                    0,
                    userId
                ).applicationInfo?.uid ?: -1
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "getPackageUid",
                    "Failed to get uid for: $packageName",
                    e
                )
                -1
            }
        }

        override fun getUserHandle(userId: Int): UserHandle? {
            return try {
                UserHandleHidden.of(userId)
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "getUserHandle",
                    "Failed to get user handle for: $userId",
                    e
                )
                null
            }
        }

        override fun setOpsMode(code: Int, uid: Int, packageName: String?, mode: Int) {
            if (packageName == null) return
            try {
                val appOpsManagerHidden = mAppOpsManager.castTo<AppOpsManagerHidden>()
                appOpsManagerHidden.setMode(code, uid, packageName, mode)
                LogHelper.d(
                    "AppManageRootService",
                    "setOpsMode",
                    "Set mode: $mode for op: $code on $packageName"
                )
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "setOpsMode",
                    "Failed to set ops mode for: $packageName",
                    e
                )
            }
        }

        override fun resetAppOps(userId: Int, packageName: String?) {
            if (packageName == null) return
            try {
                // 使用 shell 命令重置 AppOps
                val result =
                    com.topjohnwu.superuser.ShellUtils.fastCmd("appops reset --user $userId $packageName")
                LogHelper.d(
                    "AppManageRootService",
                    "resetAppOps",
                    "Reset appops for: $packageName, result: $result"
                )
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "resetAppOps",
                    "Failed to reset appops for: $packageName",
                    e
                )
            }
        }

        override fun getPackageSsaidAsUser(packageName: String, uid: Int, userId: Int): String? {
            return try {
                val settingsState = getSettingsState(userId)
                settingsState.getSettingLocked(getSsaidName(packageName, uid))?.value
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "getPackageSsaidAsUser",
                    "Failed to get ssaid for: $packageName",
                    e
                )
                null
            }
        }

        override fun setPackageSsaidAsUser(
            packageName: String,
            uid: Int,
            userId: Int,
            ssaid: String
        ) {
            try {
                val settingsState = getSettingsState(userId)
                settingsState.insertSettingLocked(
                    getSsaidName(packageName, uid),
                    ssaid,
                    null,
                    true,
                    packageName
                )
                LogHelper.d(
                    "AppManageRootService",
                    "setPackageSsaidAsUser",
                    "Set ssaid for: $packageName, ssaid: $ssaid"
                )
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "setPackageSsaidAsUser",
                    "Failed to set ssaid for: $packageName",
                    e
                )
            }
        }

        override fun isPackageRunning(packageName: String, userId: Int): Boolean {
            return try {
                // 使用 AMS (Activity Manager Service) 获取运行中的进程列表
                val runningProcesses = mActivityManagerHidden.getRunningAppProcesses()
                if (runningProcesses != null) {
                    // 检查是否有进程属于该包名
                    runningProcesses.any { process ->
                        process.processName == packageName || 
                        process.processName.startsWith("$packageName:")
                    }
                } else {
                    false
                }
            } catch (e: Exception) {
                LogHelper.e(
                    "AppManageRootService",
                    "isPackageRunning",
                    "Failed to check if package is running: $packageName",
                    e
                )
                false
            }
        }

        private fun getSettingsState(userId: Int): SettingsState {
            val lock = Object()
            val thread = HandlerThread("ssaid_handler", Process.THREAD_PRIORITY_BACKGROUND)
            thread.start()
            val file = File("/data/system/users/$userId/settings_ssaid.xml")
            val key = SettingsState.makeKey(SettingsState.SETTINGS_TYPE_SSAID, userId)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingsStateApi31(
                    lock,
                    file,
                    key,
                    SettingsState.MAX_BYTES_PER_APP_PACKAGE_UNLIMITED,
                    thread.looper
                )
            } else {
                SettingsStateApi26(
                    lock,
                    file,
                    key,
                    SettingsState.MAX_BYTES_PER_APP_PACKAGE_UNLIMITED,
                    thread.looper
                )
            }
        }

        private fun getSsaidName(packageName: String, uid: Int): String {
            return if (packageName == SettingsState.SYSTEM_PACKAGE_NAME) "userkey" else uid.toString()
        }

        private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
            if (drawable == null) return null

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


        private fun writeToParcel(context: Context, block: (Parcel) -> Unit): ParcelFileDescriptor {
            val parcel = Parcel.obtain()
            parcel.setDataPosition(0)
            block(parcel)
            val tmpFile = File.createTempFile(
                PathHelper.TMP_PARCEL_PREFIX,
                PathHelper.TMP_SUFFIX,
                context.cacheDir
            )
            tmpFile.delete()
            tmpFile.createNewFile()
            tmpFile.writeBytes(parcel.marshall())
            val pfd = ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_WRITE)
            tmpFile.delete()
            parcel.recycle()
            return pfd
        }

        private fun readFromParcel(pfd: ParcelFileDescriptor, block: (Parcel) -> Unit) {
            val stream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
            val bytes = stream.readBytes()
            val parcel = Parcel.obtain()
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            block(parcel)
            parcel.recycle()
        }
    }


}
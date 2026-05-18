package tiiehenry.android.snapshot.provider.service.handler

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManagerHidden
import android.content.pm.UserInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.RemoteException
import android.app.ActivityManagerHidden
import android.os.UserManagerHidden
import com.topjohnwu.superuser.Shell
import nota.android.io.NativeFileSystem
import tiiehenry.android.snapshot.app.AppDetail
import tiiehenry.android.snapshot.app.AppInfo
import tiiehenry.android.snapshot.app.AppStorage
import tiiehenry.android.snapshot.app.AppStorageDetail
import tiiehenry.android.snapshot.provider.appmanager.util.LogHelper
import tiiehenry.android.snapshot.provider.appmanager.util.PathHelper
import tiiehenry.android.snapshot.provider.root.PmShell
import java.io.File

class AppManagementHandler(
    private val context: Context,
    private val mPackageManager: PackageManager,
    private val mPackageManagerHidden: PackageManagerHidden,
    private val mUserManager: UserManagerHidden,
    private val mActivityManagerHidden: ActivityManagerHidden
) {

    private val CORE_SYSTEM_PACKAGES = setOf(
        "android",
        "com.android.providers.settings",
        "com.android.providers.media",
        "com.android.providers.telephony",
        "com.android.providers.contacts",
        "com.android.providers.calendar",
        "com.android.shell",
    )

    fun getInstalledAppInfos(): List<AppInfo> {
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
                    detail = AppDetail(
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
        return infos
    }

    fun getInstalledAppStorages(): List<AppStorage> {
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
                    ?.let { path -> NativeFileSystem.calculateTreeSize(path) }
            }.getOrNull() ?: 0
            val userBytes = NativeFileSystem.calculateTreeSize(
                PathHelper.getAppUserDir(userId, item.packageName)
            )
            val userDeBytes = NativeFileSystem.calculateTreeSize(
                PathHelper.getAppUserDeDir(userId, item.packageName)
            )
            val dataBytes = NativeFileSystem.calculateTreeSize(
                PathHelper.getAppDataDir(userId, item.packageName)
            )
            val obbBytes = NativeFileSystem.calculateTreeSize(
                PathHelper.getAppObbDir(userId, item.packageName)
            )
            val mediaBytes = NativeFileSystem.calculateTreeSize(
                PathHelper.getAppMediaDir(userId, item.packageName)
            )
            AppStorage(
                packageName = item.packageName,
                userId = userId,
                detail = AppStorageDetail(
                    apkBytes = apkBytes,
                    internalDataBytes = userBytes + userDeBytes,
                    externalDataBytes = dataBytes,
                    additionalDataBytes = obbBytes + mediaBytes,
                )
            )
        })
        return storages
    }

    fun getUsers(): List<UserInfo> {
        return mUserManager.users
    }

    fun getPackageSourceDir(packageName: String, userId: Int): List<String> {
        val sourceDirList = mutableListOf<String>()
        val packageInfo = mPackageManagerHidden.getPackageInfoAsUser(packageName, 0, userId)
        packageInfo.applicationInfo?.sourceDir?.also { sourceDirList.add(it) }
        val splitSourceDirs = packageInfo.applicationInfo?.splitSourceDirs
        if (!splitSourceDirs.isNullOrEmpty()) for (i in splitSourceDirs) sourceDirList.add(i)
        return sourceDirList
    }

    fun getPackageInfo(packageName: String, flags: Int, userId: Int): PackageInfo? {
        return try {
            mPackageManagerHidden.getPackageInfoAsUser(packageName, flags, userId)
        } catch (e: Exception) {
            LogHelper.e(
                "SnapshotRootService",
                "getPackageInfo",
                "Failed to get package info: $packageName: ${e.message}"
            )
            null
        }
    }

    fun getApplicationInfo(
        packageName: String,
        flags: Int,
        userId: Int
    ): ApplicationInfo? {
        return try {
            mPackageManagerHidden.getApplicationInfoAsUser(packageName, flags, userId)
        } catch (e: Exception) {
            LogHelper.e(
                "SnapshotRootService",
                "getApplicationInfo",
                "Failed to get application info: $packageName: ${e.message}"
            )
            null
        }
    }

    fun loadLabel(packageName: String, userId: Int): String? {
        return try {
            val appInfo = mPackageManagerHidden.getApplicationInfoAsUser(packageName, 0, userId)
            appInfo?.loadLabel(mPackageManager)?.toString() ?: packageName
        } catch (e: Exception) {
            LogHelper.e(
                "SnapshotRootService",
                "loadLabel",
                "Failed to load label: $packageName: ${e.message}"
            )
            null
        }
    }

    fun loadIcon(packageName: String, userId: Int): Bitmap? {
        return try {
            val appInfo = mPackageManagerHidden.getApplicationInfoAsUser(packageName, 0, userId)
            val drawable = appInfo?.loadIcon(mPackageManager)
            drawableToBitmap(drawable)
        } catch (e: Exception) {
            throw RemoteException("Failed to load icon: $packageName: ${e.message}")
        }
    }

    fun isInstalled(packageName: String, userId: Int): Boolean {
        return try {
            mPackageManagerHidden.getPackageInfoAsUser(
                packageName,
                PackageManager.GET_META_DATA,
                userId
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    fun installApk(file: String, userId: Int): Boolean {
        return try {
            val result = PmShell.install(userId, file)
//                val installCmd =
//                    "pm install -i com.android.vending -r -t -d --user $userId \"$file\""
//                val shell = Shell.Builder.create()
//                    .setFlags(Shell.FLAG_MOUNT_MASTER)
//                    .setTimeout(120)
//                    .build()
//                val result = shell.newJob().to(null, null).add(installCmd).exec()
//                shell.close()
            if (result.isSuccess) {
                LogHelper.i(
                    "SnapshotRootService",
                    "installApk",
                    "Successfully installed APK: $file"
                )
                true
            } else {
                LogHelper.e("SnapshotRootService", "installApk", "Failed to install APK: $file")
                false
            }
        } catch (e: Exception) {
            LogHelper.e("SnapshotRootService", "installApk", "Error: ${e.message}")
            false
        }
    }

    fun installApks(files: List<String>, userId: Int): Boolean {
        if (files.isEmpty()) {
            LogHelper.e("SnapshotRootService", "installApks", "Files list is empty")
            return false
        }
        return try {
            val create = PmShell.Installer.create(userId)
            val output = create.outString
            if (!create.isSuccess) {
                throw Exception("Failed to create install session: $output")
            }
            val sessionMatch = Regex("\\[(\\d+)]").find(output)
            if (sessionMatch == null) {
//                    shell.close()
                LogHelper.e(
                    "SnapshotRootService",
                    "installApks",
                    "Failed to get session ID from output: $output"
                )
                return false
            }
            val sessionId = sessionMatch.groupValues[1]
            for ((index, filePath) in files.withIndex()) {
                val file = File(filePath)
                val fileSize = file.length()
                val splitName = if (index == 0) "base" else "split_$index"
                val writeCmd =
                    "pm install-write -S $fileSize - $sessionId $splitName \"$filePath\""
                if (!PmShell.Installer.write(sessionId, splitName, filePath).isSuccess) {
                    PmShell.Installer.abandon(sessionId)
                    throw Exception("Failed to write APK: $filePath")
                }
            }
            val commit = PmShell.Installer.commit(sessionId)
            if (commit.isSuccess) {
                LogHelper.i(
                    "SnapshotRootService",
                    "installApks",
                    "Successfully installed APKs: ${files.joinToString(", ")}"
                )
                true
            } else {
                LogHelper.e(
                    "SnapshotRootService",
                    "installApks",
                    "Failed to install APKs: ${files.joinToString(", ")}"
                )
                false
            }
        } catch (e: Exception) {
            LogHelper.e("SnapshotRootService", "installApks", "Error: ${e.message}")
            false
        }
    }

    fun uninstallApk(packageName: String, userId: Int): Boolean {
        val uninstallCmd = "pm uninstall --user $userId $packageName"
        val shell = Shell.Builder.create()
            .setFlags(Shell.FLAG_MOUNT_MASTER)
            .setTimeout(60)
            .build()
        val result = shell.newJob().to(null, null).add(uninstallCmd).exec()
        shell.close()
        val output = result.out.joinToString("\n")
        val errorOutput = result.err.joinToString("\n")
        val fullOutput = "$output\n$errorOutput"

        // 检查多种成功情况
        // 1. 退出码为 0（某些系统成功时不输出任何内容）
        // 2. 命令成功且输出包含成功关键词
        val success = result.code == 0 || (
                result.isSuccess && (
                        output.contains("Success") ||
                                output.contains("success") ||
                                output.contains("not installed") ||
                                output.contains("Success!") ||
                                fullOutput.contains("package uninstalled successfully")
                        )
                )

        if (success) {
            LogHelper.i(
                "SnapshotRootService",
                "uninstallApk",
                "Successfully uninstalled APK: $packageName"
            )
            return true
        } else {
            LogHelper.e(
                "SnapshotRootService",
                "uninstallApk",
                "Failed to uninstall APK: $packageName, Output: $fullOutput, Exit code: ${result.code}"
            )
            return false
        }
    }

    fun forceStopPackage(packageName: String, userId: Int): Boolean {
        return try {
            val cmd = "am force-stop --user $userId $packageName"
            val shell = Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(30)
                .build()
            val result = shell.newJob().to(null, null).add(cmd).exec()
            shell.close()
            if (result.isSuccess) {
                LogHelper.i(
                    "SnapshotRootService",
                    "forceStopPackage",
                    "Successfully force-stopped package: $packageName"
                )
                true
            } else {
                LogHelper.e(
                    "SnapshotRootService",
                    "forceStopPackage",
                    "Failed to force-stop package: $packageName"
                )
                false
            }
        } catch (e: Exception) {
            LogHelper.e("SnapshotRootService", "forceStopPackage", "Error: ${e.message}")
            false
        }
    }

    fun clearAppData(packageName: String, userId: Int): Boolean {
        return try {
            val cmd = "pm clear --user $userId $packageName"
            val shell = Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(60)
                .build()
            val result = shell.newJob().to(null, null).add(cmd).exec()
            shell.close()
            if (result.isSuccess) {
                LogHelper.i(
                    "SnapshotRootService",
                    "clearAppData",
                    "Successfully cleared app data: $packageName"
                )
                true
            } else {
                LogHelper.e(
                    "SnapshotRootService",
                    "clearAppData",
                    "Failed to clear app data: $packageName"
                )
                false
            }
        } catch (e: Exception) {
            LogHelper.e("SnapshotRootService", "clearAppData", "Error: ${e.message}")
            false
        }
    }

    fun suspendPackage(packageName: String, userId: Int): Boolean {
        return try {
            val cmd = "pm suspend $packageName"
            val shell = Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(30)
                .build()
            val result = shell.newJob().to(null, null).add(cmd).exec()
            shell.close()
            if (result.isSuccess) {
                LogHelper.i(
                    "SnapshotRootService",
                    "suspendPackage",
                    "Successfully suspended package: $packageName"
                )
                true
            } else {
                LogHelper.e(
                    "SnapshotRootService",
                    "suspendPackage",
                    "Failed to suspend package: $packageName"
                )
                false
            }
        } catch (e: Exception) {
            LogHelper.e("SnapshotRootService", "suspendPackage", "Error: ${e.message}")
            false
        }
    }

    fun unsuspendPackage(packageName: String, userId: Int): Boolean {
        return try {
            val cmd = "pm unsuspend $packageName"
            val shell = Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(30)
                .build()
            val result = shell.newJob().to(null, null).add(cmd).exec()
            shell.close()
            if (result.isSuccess) {
                LogHelper.i(
                    "SnapshotRootService",
                    "unsuspendPackage",
                    "Successfully unsuspended package: $packageName"
                )
                true
            } else {
                LogHelper.e(
                    "SnapshotRootService",
                    "unsuspendPackage",
                    "Failed to unsuspend package: $packageName"
                )
                false
            }
        } catch (e: Exception) {
            LogHelper.e("SnapshotRootService", "unsuspendPackage", "Error: ${e.message}")
            false
        }
    }

    fun isPackageRunning(packageName: String, userId: Int): Boolean {
        return try {
            val runningProcesses = mActivityManagerHidden.getRunningAppProcesses()
            runningProcesses?.any { process ->
                process.processName == packageName ||
                        process.processName.startsWith("$packageName:")
            } ?: false
        } catch (e: Exception) {
            throw RemoteException("Failed to check if package is running: $packageName: ${e.message}")
        }
    }

    fun launchApp(packageName: String, userId: Int): Boolean {
        return try {
            val shell = Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(30)
                .build()

            // 优先检测应用是否已在后台运行，若是则通过 am moveTaskToFront 将其前台化
            val isRunning = isPackageRunning(packageName, userId)
            if (isRunning) {
                // 从 dumpsys activity tasks 中查找该包名且属于指定 userId 的 taskId
                //
                // 真实输出格式（Android 10+）：
                //   * Task{6e2c6b8 #42 visible=false mode=fullscreen translucent=false}
                //       userId=0 effectiveUid=u0a123 mCallingUid=u0a123 ...
                //       intent={...cmp=com.example.app/.MainActivity}
                //       realActivity=com.example.app/.MainActivity
                //
                // taskId 在 Task{} 中用 #<id> 表示，userId 在紧随其后的行
                val dumpsysResult = shell.newJob().to(null, null)
                    .add("dumpsys activity tasks")
                    .exec()
                val lines = dumpsysResult.out
                var taskId: Int? = null
                var pendingTaskId: Int? = null   // 当前 Task 块解析到的 taskId
                var pendingUserId: Int? = null   // 当前 Task 块解析到的 userId
                for (line in lines) {
                    // 匹配 Task 块头部："* Task{... #42 ...}" 或 "taskId=42"
                    // 两种格式都尝试提取
                    val taskHashMatch = Regex("\\* Task\\{[^}]* #(\\d+)").find(line)
                    val taskIdFieldMatch = Regex("\\btaskId=(\\d+)").find(line)
                    val newTaskId = (taskHashMatch?.groupValues?.get(1)
                        ?: taskIdFieldMatch?.groupValues?.get(1))?.toIntOrNull()
                    if (newTaskId != null) {
                        pendingTaskId = newTaskId
                        pendingUserId = null // 重置，等待后续行提供 userId
                        // 有些版本 userId 和 taskId 在同一行，一并提取
                        Regex("\\buserId=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()
                            ?.let {
                                pendingUserId = it
                            }
                    }
                    // 匹配独立的 userId 行，格式："  userId=0 ..."
                    if (pendingUserId == null) {
                        Regex("\\buserId=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()
                            ?.let {
                                pendingUserId = it
                            }
                    }
                    // 匹配含目标包名的 activity 行
                    if (line.contains(packageName) &&
                        (line.contains("baseActivity=") || line.contains("realActivity=") ||
                                line.contains("origActivity=") || line.contains("cmp=$packageName"))
                    ) {
                        if (pendingTaskId != null && pendingUserId == userId) {
                            taskId = pendingTaskId
                            break
                        }
                    }
                }
                if (taskId != null) {
                    val moveResult = shell.newJob().to(null, null)
                        .add("am moveTaskToFront $taskId")
                        .exec()
                    shell.close()
                    if (moveResult.isSuccess) return true
                    // moveTaskToFront 失败则继续降级用 am start
                }
            }

            // 应用未在运行，或 moveTaskToFront 降级：通过 am start 命令行以 root 权限启动，支持多用户
            val cmd =
                "am start --user $userId -a android.intent.action.MAIN -c android.intent.category.LAUNCHER $packageName"
            val result = shell.newJob().to(null, null).add(cmd).exec()
            shell.close()
            result.isSuccess
        } catch (e: Exception) {
            throw RemoteException("Failed to launch app: $packageName: ${e.message}")
        }
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable) {
            if (drawable.bitmap != null) return drawable.bitmap
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
}

package tiiehenry.android.snapshot.provider.service.handler

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManagerHidden
import android.content.pm.UserInfo
import android.graphics.Bitmap
import android.os.RemoteException
import nota.android.io.NativeFileSystem
import tiiehenry.android.snapshot.app.AppDetail
import tiiehenry.android.snapshot.app.AppInfo
import tiiehenry.android.snapshot.app.AppStorage
import tiiehenry.android.snapshot.app.AppStorageDetail
import tiiehenry.android.snapshot.provider.appmanager.util.LogHelper
import tiiehenry.android.snapshot.provider.appmanager.util.PathHelper
import tiiehenry.android.snapshot.provider.root.PmShell
import tiiehenry.android.snapshot.provider.utils.drawableToBitmap
import java.io.File

/**
 * 包管理委托 - 负责包查询、安装、卸载
 */
class PackageManagerDelegate(
    private val mPackageManager: PackageManager,
    private val mPackageManagerHidden: PackageManagerHidden,
    private val mUserManager: android.os.UserManagerHidden
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
        val shell = com.topjohnwu.superuser.Shell.Builder.create()
            .setFlags(com.topjohnwu.superuser.Shell.FLAG_MOUNT_MASTER)
            .setTimeout(60)
            .build()
        val result = shell.newJob().to(null, null).add(uninstallCmd).exec()
        shell.close()
        val output = result.out.joinToString("\n")
        val errorOutput = result.err.joinToString("\n")
        val fullOutput = "$output\n$errorOutput"

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
}

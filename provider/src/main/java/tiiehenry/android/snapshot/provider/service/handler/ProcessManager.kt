package tiiehenry.android.snapshot.provider.service.handler

import android.app.ActivityManagerHidden
import android.os.RemoteException
import com.topjohnwu.superuser.Shell
import tiiehenry.android.snapshot.provider.appmanager.util.LogHelper

/**
 * 进程管理器 - 负责 force-stop、clear data、suspend/unsuspend
 */
class ProcessManager(
    private val mActivityManagerHidden: ActivityManagerHidden
) {

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
}

package tiiehenry.android.snapshot.provider.service.handler

import android.os.RemoteException
import com.topjohnwu.superuser.Shell
import tiiehenry.android.snapshot.provider.appmanager.util.LogHelper

/**
 * 应用启动器 - 负责启动应用，支持 moveTaskToFront 和 am start
 */
class AppLauncher(
    private val processManager: ProcessManager
) {

    fun launchApp(packageName: String, userId: Int): Boolean {
        return try {
            val shell = Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(30)
                .build()

            val isRunning = processManager.isPackageRunning(packageName, userId)
            if (isRunning) {
                val dumpsysResult = shell.newJob().to(null, null)
                    .add("dumpsys activity tasks")
                    .exec()
                val lines = dumpsysResult.out
                var taskId: Int? = null
                var pendingTaskId: Int? = null
                var pendingUserId: Int? = null
                for (line in lines) {
                    val taskHashMatch = Regex("\\* Task\\{[^}]* #(\\d+)").find(line)
                    val taskIdFieldMatch = Regex("\\btaskId=(\\d+)").find(line)
                    val newTaskId = (taskHashMatch?.groupValues?.get(1)
                        ?: taskIdFieldMatch?.groupValues?.get(1))?.toIntOrNull()
                    if (newTaskId != null) {
                        pendingTaskId = newTaskId
                        pendingUserId = null
                        Regex("\\buserId=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()
                            ?.let {
                                pendingUserId = it
                            }
                    }
                    if (pendingUserId == null) {
                        Regex("\\buserId=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()
                            ?.let {
                                pendingUserId = it
                            }
                    }
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
                }
            }

            val cmd =
                "am start --user $userId -a android.intent.action.MAIN -c android.intent.category.LAUNCHER $packageName"
            val result = shell.newJob().to(null, null).add(cmd).exec()
            shell.close()
            result.isSuccess
        } catch (e: Exception) {
            throw RemoteException("Failed to launch app: $packageName: ${e.message}")
        }
    }
}

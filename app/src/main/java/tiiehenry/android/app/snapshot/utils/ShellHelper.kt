package tiiehenry.android.app.snapshot.utils

import android.content.Context
import com.topjohnwu.superuser.Shell

object ShellHelper {
    private const val TAG = "ShellHelper"

    private class EnvInitializer : Shell.Initializer() {
        private fun initShell(shell: Shell) {
            shell.newJob()
                .add("nsenter --mount=/proc/1/ns/mnt sh") // Switch to global namespace
                .add("set -o pipefail") // Ensure that the exit code of each command is correct.
                .exec()
        }

        override fun onInit(context: Context, shell: Shell): Boolean {
            initShell(shell)
            return true
        }
    }

    private  fun getShellBuilder(context: Context) = Shell.Builder.create()
        .setFlags(Shell.FLAG_MOUNT_MASTER)
        .setInitializers(EnvInitializer::class.java)
        .setCommands("su")
        .setTimeout(30)

     fun initMainShell(context: Context)  {
        Shell.enableVerboseLogging = true
        Shell.setDefaultBuilder(getShellBuilder(context))
    }

}

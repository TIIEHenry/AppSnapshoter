package tiiehenry.android.snapshot.provider.root

import android.content.Context
import com.topjohnwu.superuser.Shell

class EnvInitializer : Shell.Initializer() {
    companion object {
        fun initShell(shell: Shell, context: Context) {
            shell.newJob()
                .add("nsenter --mount=/proc/1/ns/mnt sh") // Switch to global namespace
//                .add("export PATH=${context.binDir()}:${USD}PATH")
                .add("export HOME=${context.filesDir}")
                .add("set -o pipefail") // Ensure that the exit code of each command is correct.
                .add("alias awk=${SymbolUtil.QUOTE}busybox awk${SymbolUtil.QUOTE}")
                .add("alias ps=${SymbolUtil.QUOTE}busybox ps${SymbolUtil.QUOTE}")
                .exec()
        }
    }

    override fun onInit(context: Context, shell: Shell): Boolean {
        initShell(shell, context)
        return true
    }
}
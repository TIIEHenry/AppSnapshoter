package tiiehenry.android.snapshot.provider.root

import android.os.Build
import android.util.Log
import com.topjohnwu.superuser.Shell

object PmShell {

    private const val TAG = "PmShell"
    private const val QUOTE = '"'

    fun execute(
        vararg args: String,
        shell: Shell? = null,
        log: Boolean = true
    ): ShellResult {
        val shellResult = ShellResult(code = -1,
            input = args.toList().filter { it.isNotEmpty() }, out = listOf())

        if (log) {
            Log.i(TAG, shellResult.inputString)
        }

        if (shell == null) {
            Shell.cmd(shellResult.inputString).exec().also { result ->
                shellResult.code = result.code
                shellResult.out = result.out
            }
        } else {
            val outList = mutableListOf<String>()
            shell.newJob().to(outList, outList).add(shellResult.inputString).exec().also { result ->
                shellResult.code = result.code
                shellResult.out = outList
            }
        }

        if (log) {
            if (shellResult.outString.trim().isNotEmpty())
                Log.i(TAG, shellResult.outString)
            Log.i(TAG, shellResult.code.toString())
        }

        return shellResult
    }

    fun install(userId: Int, src: String): ShellResult =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // pm install --user "$userId" -r -t "$src"
            execute(
                "pm",
                "install",
                "--user",
                "${QUOTE}$userId${QUOTE}",
                "-r",
                "-t",
                "-d",
                "${QUOTE}$src${QUOTE}"
            )
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU) {
            // pm install -i com.android.vending --user "$userId" -r -t "$src"
            execute(
                "pm",
                "install",
                "-i",
                "com.android.vending",
                "--user",
                "${QUOTE}$userId${QUOTE}",
                "-r",
                "-t",
                "-d",
                "${QUOTE}$src${QUOTE}"
            )
        } else {
            // pm install --bypass-low-target-sdk-block -i com.android.vending --user "$userId" -r -t "$src"
            execute(
                "pm",
                "install",
                "--bypass-low-target-sdk-block",
                "-i",
                "com.android.vending",
                "--user",
                "${QUOTE}$userId${QUOTE}",
                "-r",
                "-t",
                "-d",
                "${QUOTE}$src${QUOTE}"
            )
        }

    object Installer {
        fun create(userId: Int): ShellResult = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // pm install-create --user "$userId" -t | grep -E -o '[0-9]+'
            execute(
                "pm",
                "install-create",
                "--user",
                "${QUOTE}$userId${QUOTE}",
                "-t",
                "|",
                "grep -E -o '[0-9]+'"
            )
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU) {
            // pm install-create -i com.android.vending --user "$userId" -t | grep -E -o '[0-9]+'
            execute(
                "pm",
                "install-create",
                "-i",
                "com.android.vending",
                "--user",
                "${QUOTE}$userId${QUOTE}",
                "-t",
                "|",
                "grep -E -o '[0-9]+'"
            )
        } else {
            // pm install-create --bypass-low-target-sdk-block -i com.android.vending --user "$userId" -t | grep -E -o '[0-9]+'
            execute(
                "pm",
                "install-create",
                "--bypass-low-target-sdk-block",
                "-i",
                "com.android.vending",
                "--user",
                "${QUOTE}$userId${QUOTE}",
                "-t",
                "|",
                "grep -E -o '[0-9]+'"
            )
        }

        fun write(session: String, srcName: String, src: String): ShellResult = run {
            // pm install-write "$session" "$srcDir" "$src"
            execute(
                "pm",
                "install-write",
                "${QUOTE}$session${QUOTE}",
                "${QUOTE}$srcName${QUOTE}",
                "${QUOTE}$src${QUOTE}"
            )
        }

        fun commit(session: String): ShellResult = run {
            // pm install-commit "$session"
            execute(
                "pm", "install-commit",
                "${QUOTE}$session${QUOTE}"
            )
        }

        fun abandon(session: String): ShellResult = run {
            execute(
                "pm", "install-abandon",
                "${QUOTE}$session${QUOTE}"
            )
        }
    }
}

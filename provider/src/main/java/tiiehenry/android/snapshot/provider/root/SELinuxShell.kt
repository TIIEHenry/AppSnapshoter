package tiiehenry.android.snapshot.provider.root

import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SELinuxShell {
    private const val TAG = "SELinuxShell"
    private const val USD = '$'

    private const val QUOTE = '"'


    private suspend fun execute(
        vararg args: String,
        shell: Shell? = null,
        log: Boolean = true
    ): ShellResult = withContext(Dispatchers.IO) {
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

        shellResult
    }

    suspend fun getContext(path: String): ShellResult = run {
        // ls -Zd "$path" | awk 'NF>1{print $1}'
        execute(
            "ls",
            "-Zd",
            "${QUOTE}$path${QUOTE}",
            "|",
            "awk 'NF>1{print ${USD}1}'"
        )
    }

    suspend fun chown(uid: UInt, gid: UInt, path: String): ShellResult = run {
        // chown -hR "$uid:$uid" "$path/"
        execute(
            "chown",
            "-hR",
            "${QUOTE}$uid:$gid${QUOTE}",
            "${QUOTE}$path/${QUOTE}",
        )
    }

    suspend fun chcon(context: String, path: String): ShellResult = run {
        // chcon -hR "$context" "$path/"
        execute(
            "chcon",
            "-hR",
            "${QUOTE}$context${QUOTE}",
            "${QUOTE}$path/${QUOTE}",
        )
    }
}
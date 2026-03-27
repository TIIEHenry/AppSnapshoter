package tiiehenry.android.snapshot.provider.root

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

const val TAG_SHELL_IN = "SHELL_IN  "
const val TAG_SHELL_OUT = "SHELL_OUT "
const val TAG_SHELL_CODE = "SHELL_CODE"

object BaseUtil {
    private suspend fun getShellBuilder(context: Context) = Shell.Builder.create()
        .setFlags(Shell.FLAG_MOUNT_MASTER or Shell.FLAG_REDIRECT_STDERR)
        .setInitializers(EnvInitializer::class.java)
        .setCommands("su")
        .setTimeout(3)

    private suspend fun getNewShell(context: Context): Shell? =
        runCatching { getShellBuilder(context).build() }.getOrNull()

    suspend fun execute(
        vararg args: String,
        shell: Shell? = null,
        log: Boolean = true
    ): ShellResult = withContext(Dispatchers.IO) {
        val shellResult = ShellResult(code = -1, input = args.toList().trim(), out = listOf())

        if (log) {
            Log.i(TAG_SHELL_IN, shellResult.inputString)
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
                Log.i(TAG_SHELL_OUT, shellResult.outString)
            Log.i(TAG_SHELL_CODE, shellResult.code.toString())
        }

        shellResult
    }

    suspend fun execute(
        vararg args: String,
        shell: Shell,
        log: Boolean = true,
        timeout: Long
    ): ShellResult = withContext(Dispatchers.IO) {
        var shellResult = ShellResult(code = -1, input = args.toList().trim(), out = listOf())

        val job = launch {
            shellResult = execute(args = args, shell = shell, log = log)
        }
        delay(50)
        runCatching {
            if (timeout != -1L) {
                shell.waitAndClose(timeout, TimeUnit.SECONDS)
            } else {
                shell.waitAndClose()
            }
        }.onFailure {
            Log.i("BaseUtil", "Execution timeout.")
        }
        job.join()

        shellResult
    }

    suspend fun kill(context: Context, vararg keys: String) {
        val shell = getNewShell(context)
        if (shell != null) {
            // ps -A | grep -w $key1 | grep -w $key2 | ... | awk 'NF>1{print $1}' | xargs kill -9
            val keysArg = keys.map { "| grep -w $it" }.toTypedArray()
            execute(
                "ps -A",
                *keysArg,
                "| awk 'NF>1{print ${SymbolUtil.USD}1}'",
                "| xargs kill -9",
                shell = shell,
                timeout = -1
            )
        } else {
            Log.i("kill", "Failed to get a new shell!")
        }
    }

    suspend fun killPackage(context: Context, userId: Int, packageName: String) {
        val shell = getNewShell(context)
        if (shell != null) {
            val cmd = """
            until [[ ${SymbolUtil.USD}(dumpsys activity processes | grep "packageList" | cut -d '{' -f2 | cut -d '}' -f1 | egrep -w "$packageName" | sed -n '1p') = "" ]]; do
                killall -9 "$packageName" &>/dev/null
                am force-stop --user "$userId" "$packageName" &>/dev/null
                am kill "$packageName" &>/dev/null
            done
        """.trimIndent()
            execute(cmd, shell = shell, timeout = 3)
        } else {
            Log.i("killPackage", "Failed to get a new shell!")
        }
    }

    fun readIconFromPackageName(context: Context, pkgName: String): Drawable? =
        runCatching { context.packageManager.getApplicationIcon(pkgName) }.getOrNull()

    suspend fun readLink(pid: String) = run {
        // readlink /proc/$pid/ns/mnt
        execute(
            "readlink",
            "/proc/$pid/ns/mnt",
            log = false,
        ).outString
    }

    suspend fun readVariable(variable: String) = run {
        // echo "$variable"
        execute(
            "echo",
            "${SymbolUtil.QUOTE}${SymbolUtil.USD}$variable${SymbolUtil.QUOTE}",
            log = false,
        ).outString
    }

    suspend fun readSuVersion(su: String) = run {
        // su -v
        execute(
            su,
            "-v",
            log = false,
        ).outString
    }
}

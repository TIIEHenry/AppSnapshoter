package tiiehenry.android.snapshot.provider.root

import android.os.Build

object Pm {
    private suspend fun execute(vararg args: String): ShellResult = BaseUtil.execute("pm", *args)
    suspend fun install(userId: Int, src: String): ShellResult = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        // pm install --user "$userId" -r -t "$src"
        execute(
            "install",
            "--user",
            "${SymbolUtil.QUOTE}$userId${SymbolUtil.QUOTE}",
            "-r",
            "-t",
            "${SymbolUtil.QUOTE}$src${SymbolUtil.QUOTE}",
        )
    } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU) {
        // pm install -i com.android.vending --user "$userId" -r -t "$src"
        execute(
            "install",
            "-i",
            "com.android.vending",
            "--user",
            "${SymbolUtil.QUOTE}$userId${SymbolUtil.QUOTE}",
            "-r",
            "-t",
            "${SymbolUtil.QUOTE}$src${SymbolUtil.QUOTE}",
        )
    } else {
        // pm install --bypass-low-target-sdk-block -i com.android.vending --user "$userId" -r -t "$src"
        execute(
            "install",
            "--bypass-low-target-sdk-block",
            "-i",
            "com.android.vending",
            "--user",
            "${SymbolUtil.QUOTE}$userId${SymbolUtil.QUOTE}",
            "-r",
            "-t",
            "${SymbolUtil.QUOTE}$src${SymbolUtil.QUOTE}",
        )
    }

    object Install {
        suspend fun create(userId: Int): ShellResult = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // pm install-create --user "$userId" -t | grep -E -o '[0-9]+'
            execute(
                "install-create",
                "--user",
                "${SymbolUtil.QUOTE}$userId${SymbolUtil.QUOTE}",
                "-t",
                "|",
                "grep -E -o '[0-9]+'",
            )
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU) {
            // pm install-create -i com.android.vending --user "$userId" -t | grep -E -o '[0-9]+'
            execute(
                "install-create",
                "-i",
                "com.android.vending",
                "--user",
                "${SymbolUtil.QUOTE}$userId${SymbolUtil.QUOTE}",
                "-t",
                "|",
                "grep -E -o '[0-9]+'",
            )
        } else {
            // pm install-create --bypass-low-target-sdk-block -i com.android.vending --user "$userId" -t | grep -E -o '[0-9]+'
            execute(
                "install-create",
                "--bypass-low-target-sdk-block",
                "-i",
                "com.android.vending",
                "--user",
                "${SymbolUtil.QUOTE}$userId${SymbolUtil.QUOTE}",
                "-t",
                "|",
                "grep -E -o '[0-9]+'",
            )
        }

        suspend fun write(session: String, srcName: String, src: String): ShellResult = run {
            // pm install-write "$session" "$srcDir" "$src"
            execute(
                "install-write",
                "${SymbolUtil.QUOTE}$session${SymbolUtil.QUOTE}",
                "${SymbolUtil.QUOTE}$srcName${SymbolUtil.QUOTE}",
                "${SymbolUtil.QUOTE}$src${SymbolUtil.QUOTE}",
            )
        }

        suspend fun commit(session: String): ShellResult = run {
            // pm install-commit "$session"
            execute(
                "install-commit",
                "${SymbolUtil.QUOTE}$session${SymbolUtil.QUOTE}",
            )
        }
    }
}

package tiiehenry.android.snapshot.provider.root

object SELinux {
    private suspend fun execute(vararg args: String): ShellResult = BaseUtil.execute(*args)

    suspend fun getContext(path: String): ShellResult = run {
        // ls -Zd "$path" | awk 'NF>1{print $1}'
        execute(
            "ls",
            "-Zd",
            "${SymbolUtil.QUOTE}$path${SymbolUtil.QUOTE}",
            "|",
            "awk 'NF>1{print ${SymbolUtil.USD}1}'"
        )
    }

    suspend fun chown(uid: UInt, gid: UInt, path: String): ShellResult = run {
        // chown -hR "$uid:$uid" "$path/"
        execute(
            "chown",
            "-hR",
            "${SymbolUtil.QUOTE}$uid:$gid${SymbolUtil.QUOTE}",
            "${SymbolUtil.QUOTE}$path/${SymbolUtil.QUOTE}",
        )
    }

    suspend fun chcon(context: String, path: String): ShellResult = run {
        // chcon -hR "$context" "$path/"
        execute(
            "chcon",
            "-hR",
            "${SymbolUtil.QUOTE}$context${SymbolUtil.QUOTE}",
            "${SymbolUtil.QUOTE}$path/${SymbolUtil.QUOTE}",
        )
    }
}
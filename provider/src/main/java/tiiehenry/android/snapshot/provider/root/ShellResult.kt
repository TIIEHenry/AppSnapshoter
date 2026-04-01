package tiiehenry.android.snapshot.provider.root

data class ShellResult(
    var code: Int,
    var input: List<String>,
    var out: List<String>,
) {
    val isSuccess: Boolean
        get() = code == 0

    val inputString: String
        get() = input.joinToString(separator = " ")

    val outString: String
        get() = out.joinToString(separator = "\n")
}

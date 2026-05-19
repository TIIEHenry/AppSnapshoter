package tiiehenry.android.snapshot.provider.utils

/**
 * 规范化 tar 命令的标准错误输出
 * 去除每行开头的 packageName:root:pid: 前缀
 *
 * @param packageName 包名，用于构建前缀正则
 * @param stderr tar 命令的原始 stderr 输出
 * @return 规范化后的错误信息
 */
fun normalizeTarStdErr(packageName: String, stderr: String): String {
    if (stderr.isBlank()) return stderr
    val prefixRegex = Regex("^${Regex.escape(packageName)}:root:\\d+:\\s*")
    return stderr.lineSequence()
        .joinToString(separator = "\n") { line -> line.replace(prefixRegex, "") }
}

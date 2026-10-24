package tiiehenry.android.snapshot.provider.filesystem

/**
 * argv[0] from the existing tar builders is the word `tar`. The executable
 * path replaces it; every later argument, including `-C` and `--add-file=`,
 * stays in order.
 */
internal fun tarCliCommand(executable: String, argv: Array<String>): List<String> {
    val command = ArrayList<String>(argv.size + 1)
    command.add(executable)
    val start = if (argv.firstOrNull() == "tar") 1 else 0
    for (index in start until argv.size) command.add(argv[index])
    return command
}

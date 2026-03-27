package tiiehenry.android.snapshot.provider.root

fun List<String>.toLineString() = joinToString(separator = "\n")
fun List<String>.trim() = filter { it.isNotEmpty() }
fun List<String>.toPathString() = joinToString(separator = "/")
fun List<String>.toSpaceString() = joinToString(separator = " ")
fun List<String>.toPureString() = joinToString(separator = "")
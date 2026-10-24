package tiiehenry.android.snapshot.provider.filesystem

internal data class TarMemberSpec(val directory: String?, val member: String)

/**
 * Member names for one multi-source tar.
 * A unique final segment stays that segment. Colliding segments become paths
 * relative to the common parent of those sources, so they do not overwrite
 * each other. Names are passed with GNU tar `--add-file=`, which keeps a
 * leading `-` as part of the filename. There is no `--`: it would freeze
 * later `-C` options into filenames.
 */
internal fun multiTarMemberSpecs(paths: List<String>): List<TarMemberSpec> {
    val counts = paths.groupingBy { baseName(it) }.eachCount()
    val commonRootByBase = counts
        .filter { it.value > 1 }
        .keys
        .associateWith { base ->
            commonParentDirectory(paths.filter { baseName(it) == base })
        }
    return paths.map { path ->
        val base = baseName(path)
        val root = commonRootByBase[base]
        if (root == null) {
            TarMemberSpec(parentPath(path), base)
        } else {
            TarMemberSpec(root, relativeToDirectory(root, path))
        }
    }
}

internal fun buildMultiTarCommand(targetFile: String, paths: List<String>): List<String> {
    val args = mutableListOf("tar", "-cpf", targetFile)
    multiTarMemberSpecs(paths).forEach { spec ->
        spec.directory?.let { directory ->
            args += "-C"
            args += directory
        }
        args += "--add-file=${spec.member}"
    }
    return args
}

internal fun baseName(path: String): String =
    normalizeAbsolute(path).substringAfterLast('/')

internal fun parentPath(path: String): String? {
    val normalized = normalizeAbsolute(path)
    if (normalized == "/") return null
    val parents = parentSegments(normalized)
    return if (parents.isEmpty()) "/" else "/" + parents.joinToString("/")
}

internal fun commonParentDirectory(paths: List<String>): String {
    require(paths.isNotEmpty())
    val parents = paths.map { parentSegments(it) }
    val limit = parents.minOf { it.size }
    val common = mutableListOf<String>()
    for (index in 0 until limit) {
        val segment = parents[0][index]
        if (parents.all { it[index] == segment }) common += segment else break
    }
    return if (common.isEmpty()) "/" else "/" + common.joinToString("/")
}

internal fun relativeToDirectory(root: String, path: String): String {
    val normalizedRoot = if (root == "/") "/" else root.trimEnd('/')
    val normalizedPath = normalizeAbsolute(path)
    val prefix = if (normalizedRoot == "/") "/" else "$normalizedRoot/"
    return normalizedPath.removePrefix(prefix)
}

private fun parentSegments(path: String): List<String> {
    val segments = normalizeAbsolute(path).split('/').filter { it.isNotEmpty() }
    return if (segments.isEmpty()) emptyList() else segments.dropLast(1)
}

private fun normalizeAbsolute(path: String): String {
    val trimmed = path.trimEnd('/')
    return if (trimmed.isEmpty()) "/" else trimmed
}

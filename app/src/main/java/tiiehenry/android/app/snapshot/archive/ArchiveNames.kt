package tiiehenry.android.app.snapshot.archive

import tiiehenry.android.app.snapshot.config.CompressItems
import java.io.IOException
import java.nio.file.Paths

internal const val EXTRA_PAYLOAD_SUFFIX = ".tar.zst"

/** Task keys that an enabled extra item must not reuse. */
internal val SNAPSHOT_RESERVED_TASK_KEYS: Set<String> =
    CompressItems.all + setOf("meta-info", "uninstall-app")

/**
 * Enabled extra names collide when they repeat a task key, repeat a manifest
 * entry, or reuse a built-in / meta-info / uninstall-app identifier.
 */
internal fun extraItemNamesConflict(
    enabledNames: List<String>,
    reservedTaskKeys: Set<String> = SNAPSHOT_RESERVED_TASK_KEYS,
): Boolean {
    val manifestNames = HashSet<String>()
    for (name in enabledNames) {
        val manifestName = "$name.json"
        if (name in reservedTaskKeys || manifestName in manifestNames) return true
        manifestNames.add(manifestName)
    }
    return false
}

/** `directory.resolve(name + suffix).normalize()` still lies inside [directory]. */
internal fun archiveChildStaysInside(directory: String, name: String, suffix: String): Boolean {
    val root = Paths.get(directory).toAbsolutePath().normalize()
    val resolved = root.resolve(name + suffix).normalize()
    return resolved.startsWith(root) && resolved != root
}

/**
 * Name and path checks both pass before any extra item is compressed.
 * A conflict or a name that normalizes outside [archiveDir] throws and does not call [compress].
 */
internal fun prepareExtraItemCompression(
    archiveDir: String,
    enabledNames: List<String>,
    compress: (String) -> Unit,
) {
    if (extraItemNamesConflict(enabledNames)) {
        throw IOException("extra item name conflict")
    }
    for (name in enabledNames) {
        if (!archiveChildStaysInside(archiveDir, name, EXTRA_PAYLOAD_SUFFIX)) {
            throw IOException(name)
        }
    }
    enabledNames.forEach(compress)
}

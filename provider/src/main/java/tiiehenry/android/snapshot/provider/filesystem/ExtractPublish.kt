package tiiehenry.android.snapshot.provider.filesystem

import java.io.IOException

internal data class ExtractScratch(val temp: String, val backup: String)

/** Sibling directories of [targetDir], never children of it. */
internal fun extractScratch(targetDir: String, token: String): ExtractScratch {
    val normalized = targetDir.trimEnd('/').ifEmpty { "/" }
    val slash = normalized.lastIndexOf('/')
    val parent = if (slash <= 0) "/" else normalized.substring(0, slash)
    val name = normalized.substring(slash + 1).ifEmpty { "root" }
    return ExtractScratch(
        temp = "$parent/.$name.extract-$token",
        backup = "$parent/.$name.backup-$token"
    )
}

internal fun shouldCommitExtract(cancelled: Boolean, extracted: Boolean): Boolean =
    !cancelled && extracted

/**
 * Publish [temp] onto [target]. On failure the previous [target] tree is moved
 * back from [backup] and [temp] is removed.
 */
internal fun publishExtractedDirectory(
    temp: String,
    target: String,
    backup: String,
    exists: (String) -> Boolean,
    move: (String, String) -> Boolean,
    delete: (String) -> Boolean
): Boolean {
    val hadOriginal = exists(target)
    if (hadOriginal && !move(target, backup)) {
        delete(temp)
        return false
    }
    if (!move(temp, target)) {
        val restored = !hadOriginal || move(backup, target)
        delete(temp)
        if (!restored) {
            throw IOException("Failed to publish $target and restore $backup")
        }
        return false
    }
    if (hadOriginal) delete(backup)
    return true
}

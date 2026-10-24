package tiiehenry.android.snapshot.provider.filesystem

import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.NoSuchFileException

internal enum class FsKind { MISSING, FILE, DIRECTORY, SYMLINK, OTHER }

internal data class FsStatus(val kind: FsKind, val uid: Int = -1, val gid: Int = -1)

/** lstat-style operations. Directory symlinks must not be followed. */
internal interface LinkOps {
    fun status(path: String): FsStatus
    fun unlink(path: String)
    fun readLink(path: String): String
    fun symlink(target: String, path: String)
    fun list(path: String): List<String>?
    fun removeEmptyDirectory(path: String)
    fun mkdirs(path: String)
    fun copyFile(source: String, target: String)
    fun lchown(path: String, uid: Int, gid: Int)
    fun writeFile(path: String, text: String)
}

internal fun childPath(parent: String, name: String): String =
    if (parent == "/") "/$name" else "$parent/$name"

/**
 * Delete, copy, and chown stop at a symlink. The link is removed or recreated;
 * the target's entries and owner stay as they were.
 */
internal class LinkSafeFiles(private val ops: LinkOps) {
    fun deleteRecursively(path: String): Boolean = runCatching { deleteTree(path) }.isSuccess

    fun writeText(path: String, text: String): Boolean = runCatching {
        when (ops.status(path).kind) {
            FsKind.MISSING -> Unit
            FsKind.SYMLINK -> ops.unlink(path)
            FsKind.DIRECTORY -> throw IOException("Cannot write text over directory: $path")
            FsKind.FILE, FsKind.OTHER -> Unit
        }
        ops.writeFile(path, text)
    }.isSuccess

    fun copyRecursively(source: String, target: String, overwrite: Boolean): Boolean =
        runCatching { copyEntry(source, target, overwrite) }.isSuccess

    fun setUid(path: String, uid: Int): Boolean =
        runCatching { ops.lchown(path, uid, -1) }.isSuccess

    fun setGid(path: String, gid: Int): Boolean =
        runCatching { ops.lchown(path, -1, gid) }.isSuccess

    private fun deleteTree(path: String) {
        when (ops.status(path).kind) {
            FsKind.MISSING -> throw NoSuchFileException(path)
            FsKind.SYMLINK, FsKind.FILE, FsKind.OTHER -> ops.unlink(path)
            FsKind.DIRECTORY -> {
                val children = ops.list(path) ?: throw IOException(path)
                children.forEach { deleteTree(childPath(path, it)) }
                ops.removeEmptyDirectory(path)
            }
        }
    }

    private fun copyEntry(source: String, target: String, overwrite: Boolean) {
        when (ops.status(source).kind) {
            FsKind.MISSING -> throw NoSuchFileException(source)
            FsKind.SYMLINK -> {
                replaceExisting(target, overwrite)
                ops.symlink(ops.readLink(source), target)
            }
            FsKind.DIRECTORY -> {
                when (ops.status(target).kind) {
                    FsKind.SYMLINK -> {
                        if (!overwrite) throw FileAlreadyExistsException(target)
                        ops.unlink(target)
                        ops.mkdirs(target)
                    }
                    FsKind.FILE, FsKind.OTHER -> {
                        if (!overwrite) throw FileAlreadyExistsException(target)
                        ops.unlink(target)
                        ops.mkdirs(target)
                    }
                    FsKind.MISSING -> ops.mkdirs(target)
                    FsKind.DIRECTORY -> Unit
                }
                val children = ops.list(source) ?: throw IOException(source)
                children.forEach { name ->
                    copyEntry(childPath(source, name), childPath(target, name), overwrite)
                }
            }
            FsKind.FILE, FsKind.OTHER -> {
                replaceExisting(target, overwrite)
                ops.copyFile(source, target)
            }
        }
    }

    /** A symlink is unlinked and not traversed. A real non-empty directory is not removed. */
    private fun replaceExisting(path: String, overwrite: Boolean) {
        val kind = ops.status(path).kind
        if (kind == FsKind.MISSING) return
        if (!overwrite) throw FileAlreadyExistsException(path)
        when (kind) {
            FsKind.SYMLINK, FsKind.FILE, FsKind.OTHER -> ops.unlink(path)
            FsKind.DIRECTORY -> ops.removeEmptyDirectory(path)
            FsKind.MISSING -> Unit
        }
    }
}

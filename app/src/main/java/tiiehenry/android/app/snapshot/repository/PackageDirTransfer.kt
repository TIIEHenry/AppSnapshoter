package tiiehenry.android.app.snapshot.repository

import tiiehenry.android.app.snapshot.group.MoveAppResult
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.fs.IFileType
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

internal fun isCompletelyEmptyDir(fileSystem: IFileSystem, path: String): Boolean {
    if (!fileSystem.exists(path)) return true
    if (fileSystem.fileType(path) != IFileType.TYPE_DIR) return false
    val entries = fileSystem.listDir(path) ?: return false
    return entries.none { it != "." && it != ".." }
}

private data class RelativeTree(
    val files: Map<String, Long>,
    val directories: Set<String>,
    val listed: Boolean,
)

private fun collectRelativeTree(fileSystem: IFileSystem, root: String): RelativeTree {
    val files = linkedMapOf<String, Long>()
    val directories = linkedSetOf<String>()
    var listed = true
    fun walk(dir: String, rel: String) {
        val names = fileSystem.listDir(dir)
        if (names == null) {
            listed = false
            return
        }
        for (name in names) {
            if (name == "." || name == "..") continue
            val child = Paths.get(dir, name).absolutePathString()
            val childRel = if (rel.isEmpty()) name else "$rel/$name"
            when (fileSystem.fileType(child)) {
                IFileType.TYPE_DIR -> {
                    directories += childRel
                    walk(child, childRel)
                }
                IFileType.TYPE_FILE -> files[childRel] = fileSystem.length(child)
            }
        }
    }
    if (fileSystem.exists(root) && fileSystem.fileType(root) == IFileType.TYPE_DIR) {
        walk(root, "")
    }
    return RelativeTree(files, directories, listed)
}

/** 复制之后确认源文件都已到达目标。不要求目标没有多余文件。 */
private fun isCompleteRelativeTo(
    fileSystem: IFileSystem,
    source: String,
    target: String,
): Boolean {
    val src = collectRelativeTree(fileSystem, source)
    val dst = collectRelativeTree(fileSystem, target)
    if (!src.listed || !dst.listed) return false
    if (src.files.isEmpty()) return dst.files.isEmpty() && dst.directories.isEmpty()
    for ((rel, size) in src.files) {
        if (dst.files[rel] != size) return false
    }
    return true
}

private fun isIdenticalRelativeTo(
    fileSystem: IFileSystem,
    source: String,
    target: String,
): Boolean {
    val src = collectRelativeTree(fileSystem, source)
    val dst = collectRelativeTree(fileSystem, target)
    if (!src.listed || !dst.listed) return false
    return src.files == dst.files && src.directories == dst.directories
}

/** 目标每个文件都能在源里找到同长度对应项，且至少缺一个源文件。目标多出来的路径不是子集。 */
private fun isStrictSubsetResidual(
    fileSystem: IFileSystem,
    source: String,
    target: String,
): Boolean {
    val src = collectRelativeTree(fileSystem, source)
    val dst = collectRelativeTree(fileSystem, target)
    if (!src.listed || !dst.listed) return false
    if (dst.files.isEmpty() && dst.directories.isEmpty()) return false
    for ((rel, size) in dst.files) {
        if (src.files[rel] != size) return false
    }
    if (dst.directories.any { it !in src.directories }) return false
    return src.files.keys.any { it !in dst.files }
}

/**
 * 目标已存在时：完全一致则删源并 [onMoved]；空目录或真子集残留则先删目标再移动；
 * 有源里没有的路径或长度不同则 [MoveAppResult.TargetNonEmpty]，且不删除目标。
 * [onMoved] 只在移动完成时调用，调用方在其中写图标和元数据。
 */
internal suspend fun transferPackageDirectory(
    fileSystem: IFileSystem,
    sourceDir: String,
    targetDir: String,
    onMoved: suspend () -> Unit,
): MoveAppResult {
    if (fileSystem.exists(targetDir)) {
        when {
            isCompletelyEmptyDir(fileSystem, targetDir) -> {
                if (!fileSystem.delete(targetDir)) {
                    return MoveAppResult.Error("delete target failed")
                }
            }
            isIdenticalRelativeTo(fileSystem, sourceDir, targetDir) -> {
                if (!fileSystem.delete(sourceDir)) {
                    return MoveAppResult.Error("source delete failed")
                }
                onMoved()
                return MoveAppResult.Moved
            }
            isStrictSubsetResidual(fileSystem, sourceDir, targetDir) -> {
                if (!fileSystem.delete(targetDir)) {
                    return MoveAppResult.Error("delete target failed")
                }
            }
            else -> return MoveAppResult.TargetNonEmpty
        }
    }

    val moved = fileSystem.move(sourceDir, targetDir)
    if (!moved) {
        val copied = fileSystem.copyRecursively(sourceDir, targetDir, false)
        if (!copied || !isCompleteRelativeTo(fileSystem, sourceDir, targetDir)) {
            if (fileSystem.exists(targetDir)) {
                runCatching { fileSystem.delete(targetDir) }
            }
            return MoveAppResult.Error("move/copy failed")
        }
        if (!fileSystem.delete(sourceDir)) {
            return MoveAppResult.Error("copied but source delete failed")
        }
    }
    onMoved()
    return MoveAppResult.Moved
}

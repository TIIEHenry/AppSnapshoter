package tiiehenry.android.app.snapshot.repository

import java.nio.file.Paths

/**
 * 分组集成员关系：path 派生（纯函数，可单测）。
 */
object GroupSetMembership {

    fun normalizePath(path: String): String {
        if (path.isEmpty()) return path
        return Paths.get(path).normalize().toString().trimEnd('/')
    }

    fun parentPath(path: String): String? {
        val normalized = normalizePath(path)
        val parent = Paths.get(normalized).parent ?: return null
        return parent.toString().trimEnd('/')
    }

    fun basename(path: String): String {
        return Paths.get(normalizePath(path)).fileName?.toString().orEmpty()
    }

    /**
     * group ∈ set 当且仅当 normalize(group.path).parent == normalize(set.path)
     */
    fun isMemberOf(groupPath: String, setPath: String): Boolean {
        val parent = parentPath(groupPath) ?: return false
        return parent == normalizePath(setPath)
    }

    /**
     * 子目录名像包名（含 `.`）且没有 group.json 时跳过；此处只判断「像包名」。
     */
    fun looksLikePackageName(dirName: String): Boolean {
        if (dirName.startsWith(".")) return false
        return dirName.contains('.')
    }
}

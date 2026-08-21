package tiiehenry.android.app.snapshot.group

/**
 * 存档 Tab 顶层块顺序项。序列化进 [tiiehenry.android.app.snapshot.config.GlobalConfig.archiveRoots]。
 */
sealed class ArchiveRoot {
    data class Set(val setId: String) : ArchiveRoot()
    data class Group(val groupId: String) : ArchiveRoot()

    fun encode(): String = when (this) {
        is Set -> "s:$setId"
        is Group -> "g:$groupId"
    }

    companion object {
        fun decode(token: String): ArchiveRoot? {
            if (token.startsWith("s:") && token.length > 2) {
                return Set(token.substring(2))
            }
            if (token.startsWith("g:") && token.length > 2) {
                return Group(token.substring(2))
            }
            return null
        }

        fun encodeList(roots: List<ArchiveRoot>): String =
            roots.joinToString(",") { it.encode() }

        fun decodeList(encoded: String): List<ArchiveRoot> =
            encoded.split(",")
                .mapNotNull { it.trim().takeIf { t -> t.isNotEmpty() } }
                .mapNotNull { decode(it) }
    }
}

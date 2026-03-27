package tiiehenry.android.app.snapshot.app.tag

/**
 * 应用标签数据类
 */
data class AppTag(
    val id: String,
    val name: String,
    val type: TagType
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppTag) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    companion object {
        // 内置标签ID
        const val TAG_XPOSED = "tag_xposed"

        // 内置标签
        val XPOSED_TAG = AppTag(TAG_XPOSED, "Xposed", TagType.BUILTIN)
    }
}


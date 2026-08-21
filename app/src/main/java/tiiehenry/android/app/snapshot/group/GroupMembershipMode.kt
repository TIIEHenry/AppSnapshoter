package tiiehenry.android.app.snapshot.group

/**
 * 分组成员模式。存于 [tiiehenry.android.app.snapshot.config.GroupConfigData.membershipMode]。
 * 缺省 / 未知值按 [EXCLUSIVE]。
 */
enum class GroupMembershipMode {
    EXCLUSIVE,
    SHARED;

    fun toStorage(): String = name.lowercase()

    companion object {
        fun fromStorage(raw: String?): GroupMembershipMode =
            if (raw.equals("shared", ignoreCase = true)) SHARED else EXCLUSIVE
    }
}

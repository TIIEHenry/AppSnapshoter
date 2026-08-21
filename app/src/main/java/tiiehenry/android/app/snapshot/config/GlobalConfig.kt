package tiiehenry.android.app.snapshot.config

import com.tencent.mmkv.MMKV
import tiiehenry.android.app.snapshot.group.ArchiveRoot

object GlobalConfig {
    private const val KEY_GROUPS = "groups"
    private const val KEY_GROUPS_ORDER = "groups_order"
    private const val KEY_ARCHIVE_ROOTS = "archive_roots"
    private const val KEY_TIMELINE_PRESET = "timeline_preset"
    private const val KEY_TIMELINE_CUSTOM_START = "timeline_custom_start"
    private const val KEY_TIMELINE_CUSTOM_END = "timeline_custom_end"

    private val mmkv: MMKV
        get() = MMKV.defaultMMKV()

    /**
     * 本机全部 SnapGroup ID **登记表**（含集内分组）。
     * List 顺序无 UI 语义；存档 Tab 顶层顺序见 [archiveRoots]。
     * 仍双写 Set 以兼容旧版本读取。
     */
    var groups: List<String>
        get() {
            val orderStr = mmkv.decodeString(KEY_GROUPS_ORDER, "")
            return if (orderStr.isNullOrEmpty()) {
                mmkv.decodeStringSet(KEY_GROUPS, emptySet())?.toList() ?: emptyList()
            } else {
                orderStr.split(",").filter { it.isNotEmpty() }
            }
        }
        set(value) {
            mmkv.encode(KEY_GROUPS_ORDER, value.joinToString(","))
            mmkv.encode(KEY_GROUPS, value.toSet())
        }

    /**
     * 存档 Tab 顶层块顺序的唯一可写真源。
     * `s:{setId}` = 分组集；`g:{groupId}` = 独立分组。
     * 本机集登记 == 其中的 `s:` 项。
     *
     * 迁移：仅当键不存在时，用当前 [groups] 生成全 `g:` 并写出键；
     * 已迁过的空列表保持为空，不得把空串当成未迁移再 flatten。
     */
    var archiveRoots: List<ArchiveRoot>
        get() {
            ensureArchiveRootsMigrated()
            val encoded = mmkv.decodeString(KEY_ARCHIVE_ROOTS, "") ?: ""
            return ArchiveRoot.decodeList(encoded)
        }
        set(value) {
            mmkv.encode(KEY_ARCHIVE_ROOTS, ArchiveRoot.encodeList(value))
        }

    /** 本机已登记的分组集 ID（来自 archiveRoots 的 s: 项，不另存表）。 */
    val groupSetIds: List<String>
        get() = archiveRoots.mapNotNull { (it as? ArchiveRoot.Set)?.setId }

    /**
     * 仅当 [KEY_ARCHIVE_ROOTS] 键不存在时迁移。调用方可显式触发（例如测试）。
     * @return true 如果刚完成迁移
     */
    fun ensureArchiveRootsMigrated(): Boolean {
        if (mmkv.containsKey(KEY_ARCHIVE_ROOTS)) return false
        val migrated = groups.map { ArchiveRoot.Group(it) }
        mmkv.encode(KEY_ARCHIVE_ROOTS, ArchiveRoot.encodeList(migrated))
        return true
    }

    /** 测试用：清除 archive_roots 键（模拟未迁移）。 */
    fun clearArchiveRootsKeyForTest() {
        mmkv.removeValueForKey(KEY_ARCHIVE_ROOTS)
    }

    var timelinePreset: String?
        get() = mmkv.decodeString(KEY_TIMELINE_PRESET, null)
        set(value) { mmkv.encode(KEY_TIMELINE_PRESET, value) }

    var timelineCustomStart: Long
        get() = mmkv.decodeLong(KEY_TIMELINE_CUSTOM_START, 0L)
        set(value) { mmkv.encode(KEY_TIMELINE_CUSTOM_START, value) }

    var timelineCustomEnd: Long
        get() = mmkv.decodeLong(KEY_TIMELINE_CUSTOM_END, 0L)
        set(value) { mmkv.encode(KEY_TIMELINE_CUSTOM_END, value) }
}

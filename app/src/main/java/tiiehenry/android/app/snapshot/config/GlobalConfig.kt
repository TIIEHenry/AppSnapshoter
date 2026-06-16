package tiiehenry.android.app.snapshot.config

import com.tencent.mmkv.MMKV

object GlobalConfig {
    private const val KEY_GROUPS = "groups"
    private const val KEY_GROUPS_ORDER = "groups_order"
    private const val KEY_TIMELINE_PRESET = "timeline_preset"
    private const val KEY_TIMELINE_CUSTOM_START = "timeline_custom_start"
    private const val KEY_TIMELINE_CUSTOM_END = "timeline_custom_end"

    private val mmkv: MMKV
        get() = MMKV.defaultMMKV()

    /**
     * 分组ID列表，按顺序保存
     * 使用逗号分隔的字符串存储顺序
     */
    var groups: List<String>
        get() {
            val orderStr = mmkv.decodeString(KEY_GROUPS_ORDER, "")
            return if (orderStr.isNullOrEmpty()) {
                // 兼容旧版本：从Set读取
                mmkv.decodeStringSet(KEY_GROUPS, emptySet())?.toList() ?: emptyList()
            } else {
                orderStr.split(",").filter { it.isNotEmpty() }
            }
        }
        set(value) {
            // 保存顺序
            mmkv.encode(KEY_GROUPS_ORDER, value.joinToString(","))
            // 同时保存到Set用于兼容
            mmkv.encode(KEY_GROUPS, value.toSet())
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

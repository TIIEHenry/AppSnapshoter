package tiiehenry.android.app.snapshotor.config

import com.tencent.mmkv.MMKV
import tiiehenry.android.app.snapshotor.SnapShotApp

object GlobalConfig {
    private const val KEY_GROUPS = "groups"
    private const val KEY_GROUPS_ORDER = "groups_order"

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
}

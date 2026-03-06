package tiiehenry.android.app.snapshotor.config

import com.tencent.mmkv.MMKV
import tiiehenry.android.app.snapshotor.SnapShotApp

object GlobalConfig {
    private const val KEY_GROUPS = "groups"
    private const val KEY_GROUPS_ORDER = "groups_order"
    private const val KEY_ROOT_PATH = "rootPath"
    private const val KEY_FILE_SYSTEM_PROVIDER = "fileSystemProvider"
    private const val KEY_APP_MANAGER_PROVIDER = "appManagerProvider"
    private const val KEY_DATA_SYNC_PROVIDER = "dataSyncProvider"
    private const val KEY_SYNC_SYSTEMS = "syncSystems"

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
    
    /**
     * 分组ID集合（无序，用于快速查找）
     */
    val groupsSet: Set<String>
        get() = groups.toSet()

    var rootPath: String
        get() = mmkv.decodeString(KEY_ROOT_PATH, SnapShotApp.getInstance().defaultRootPath)
            ?: SnapShotApp.getInstance().defaultRootPath
        set(value) {
            mmkv.encode(KEY_ROOT_PATH, value)
        }

    var syncSystems: Set<String>
        get() = mmkv.decodeStringSet(KEY_SYNC_SYSTEMS) ?: emptySet()
        set(value) {
            mmkv.encode(KEY_SYNC_SYSTEMS, value)
        }

    var fileSystemProvider: String
        get() = mmkv.decodeString(KEY_FILE_SYSTEM_PROVIDER) ?: ""
        set(value) {
            mmkv.encode(KEY_FILE_SYSTEM_PROVIDER, value)
        }

    var appManagerProvider: String
        get() = mmkv.decodeString(KEY_APP_MANAGER_PROVIDER) ?: ""
        set(value) {
            mmkv.encode(KEY_APP_MANAGER_PROVIDER, value)
        }

    var dataSyncProvider: String
        get() = mmkv.decodeString(KEY_DATA_SYNC_PROVIDER) ?: ""
        set(value) {
            mmkv.encode(KEY_DATA_SYNC_PROVIDER, value)
        }
}

package tiieherny.android.app.snapshotor.config

import com.tencent.mmkv.MMKV

class GroupConfig(val groupId: String) {
    private val mmkv: MMKV = MMKV.mmkvWithID("group:$groupId")

    private companion object {
        const val KEY_ROOT_PATH = "rootPath"
        const val KEY_AUTO_SNAPSHOT = "autoSnapshot"
        const val KEY_SORT_TYPE = "sortType"
        const val KEY_SORT_REVERSE = "sortReverse"
        const val KEY_SORT_ORDER = "sortOrder"
        const val KEY_COMPRESS_ALGORITHM = "compressAlgorithm"
        const val KEY_COMPRESS_ITEMS = "compressItems"
        const val KEY_PERMISSION = "permission"
        const val KEY_ENABLE_SYNC_TO_TARGET = "enableSyncToTarget"
        const val KEY_SYNC_TARGETS = "syncTargets"
        const val KEY_ENABLE_SYNC_TO_SYSTEM = "enableSyncToSystem"
        const val KEY_SYNC_SYSTEMS = "syncSystems"
        const val KEY_SYNC_TYPE = "syncType"
    }

    var rootPath: String
        get() = mmkv.decodeString(KEY_ROOT_PATH) ?: GlobalConfig.rootPath
        set(value) {
            mmkv.encode(KEY_ROOT_PATH, value)
        }

    var autoSnapshot: Boolean
        get() = mmkv.decodeBool(KEY_AUTO_SNAPSHOT, false)
        set(value) {
            mmkv.encode(KEY_AUTO_SNAPSHOT, value)
        }

    var sortType: Int
        get() = mmkv.decodeInt(KEY_SORT_TYPE, 0)
        set(value) {
            mmkv.encode(KEY_SORT_TYPE, value)
        }

    var sortReverse: Boolean
        get() = mmkv.decodeBool(KEY_SORT_REVERSE, false)
        set(value) {
            mmkv.encode(KEY_SORT_REVERSE, value)
        }

    var sortOrder: Set<String>
        get() = mmkv.decodeStringSet(KEY_SORT_ORDER, emptySet()) ?: emptySet()
        set(value) {
            mmkv.encode(KEY_SORT_ORDER, value)
        }

    var compressAlgorithm: String
        get() = mmkv.decodeString(KEY_COMPRESS_ALGORITHM) ?: GlobalConfig.compressAlgorithm
        set(value) {
            mmkv.encode(KEY_COMPRESS_ALGORITHM, value)
        }

    var compressItems: Set<String>
        get() = mmkv.decodeStringSet(
            KEY_COMPRESS_ITEMS,
            setOf("apk", "data", "user", "user_de", "obb")
        ) ?: emptySet()
        set(value) {
            mmkv.encode(KEY_COMPRESS_ITEMS, value)
        }

    var permission: Boolean
        get() = mmkv.decodeBool(KEY_PERMISSION, false)
        set(value) {
            mmkv.encode(KEY_PERMISSION, value)
        }

    var enableSyncToTarget: Boolean
        get() = mmkv.decodeBool(KEY_ENABLE_SYNC_TO_TARGET, false)
        set(value) {
            mmkv.encode(KEY_ENABLE_SYNC_TO_TARGET, value)
        }

    var syncTargets: Set<String>
        get() = mmkv.decodeStringSet(KEY_SYNC_TARGETS, emptySet()) ?: emptySet()
        set(value) {
            mmkv.encode(KEY_SYNC_TARGETS, value)
        }

    var enableSyncToSystem: Boolean
        get() = mmkv.decodeBool(KEY_ENABLE_SYNC_TO_SYSTEM, false)
        set(value) {
            mmkv.encode(KEY_ENABLE_SYNC_TO_SYSTEM, value)
        }

    var syncSystems: Set<String>
        get() = mmkv.decodeStringSet(KEY_SYNC_SYSTEMS, emptySet()) ?: emptySet()
        set(value) {
            mmkv.encode(KEY_SYNC_SYSTEMS, value)
        }

    var syncType: Int
        get() = mmkv.decodeInt(KEY_SYNC_TYPE, 1)
        set(value) {
            mmkv.encode(KEY_SYNC_TYPE, value)
        }

    fun getMMKV(): MMKV = mmkv
}

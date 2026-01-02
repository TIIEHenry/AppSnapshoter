package tiieherny.android.app.snapshotor.config

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.TypeReference
import com.tencent.mmkv.MMKV

data class ExtraCompressItem(
    val name: String,
    val path: String,
    val excludes: List<String> = emptyList()
)

class AppConfig(val packageName: String) {
    private val mmkv: MMKV = MMKV.mmkvWithID("app:$packageName")

    private companion object {
        const val KEY_COMPRESS_ALGORITHM = "compressAlgorithm"
        const val KEY_COMPRESS_ITEMS = "compressItems"
        const val KEY_EXTRA_ITEMS = "extraItems"
        const val KEY_PERMISSION = "permission"
        const val KEY_ENABLE_SYNC_TO_TARGET = "enableSyncToTarget"
        const val KEY_SYNC_TARGETS = "syncTargets"
        const val KEY_ENABLE_SYNC_TO_SYSTEM = "enableSyncToSystem"
        const val KEY_SYNC_SYSTEMS = "syncSystems"
        const val KEY_SYNC_TYPE = "syncType"
    }

    var compressAlgorithm: String?
        get() = mmkv.decodeString(KEY_COMPRESS_ALGORITHM)
        set(value) {
            if (value != null) {
                mmkv.encode(KEY_COMPRESS_ALGORITHM, value)
            } else {
                mmkv.remove(KEY_COMPRESS_ALGORITHM)
            }
        }

    var compressItems: Set<String>?
        get() = mmkv.decodeStringSet(KEY_COMPRESS_ITEMS)
        set(value) {
            if (value != null) {
                mmkv.encode(KEY_COMPRESS_ITEMS, value)
            } else {
                mmkv.remove(KEY_COMPRESS_ITEMS)
            }
        }

    var extraItems: List<ExtraCompressItem>
        get() {
            val json = mmkv.decodeString(KEY_EXTRA_ITEMS) ?: return emptyList()
            return try {
                JSON.parseObject(json, object : TypeReference<List<ExtraCompressItem>>() {})
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(value) {
            mmkv.encode(KEY_EXTRA_ITEMS, JSON.toJSONString(value))
        }

    var permission: Boolean?
        get() = if (mmkv.containsKey(KEY_PERMISSION)) mmkv.decodeBool(KEY_PERMISSION) else null
        set(value) {
            if (value != null) {
                mmkv.encode(KEY_PERMISSION, value)
            } else {
                mmkv.remove(KEY_PERMISSION)
            }
        }

    var enableSyncToTarget: Boolean?
        get() = if (mmkv.containsKey(KEY_ENABLE_SYNC_TO_TARGET)) mmkv.decodeBool(KEY_ENABLE_SYNC_TO_TARGET) else null
        set(value) {
            if (value != null) {
                mmkv.encode(KEY_ENABLE_SYNC_TO_TARGET, value)
            } else {
                mmkv.remove(KEY_ENABLE_SYNC_TO_TARGET)
            }
        }

    var syncTargets: Set<String>?
        get() = mmkv.decodeStringSet(KEY_SYNC_TARGETS)
        set(value) {
            if (value != null) {
                mmkv.encode(KEY_SYNC_TARGETS, value)
            } else {
                mmkv.remove(KEY_SYNC_TARGETS)
            }
        }

    var enableSyncToSystem: Boolean?
        get() = if (mmkv.containsKey(KEY_ENABLE_SYNC_TO_SYSTEM)) mmkv.decodeBool(KEY_ENABLE_SYNC_TO_SYSTEM) else null
        set(value) {
            if (value != null) {
                mmkv.encode(KEY_ENABLE_SYNC_TO_SYSTEM, value)
            } else {
                mmkv.remove(KEY_ENABLE_SYNC_TO_SYSTEM)
            }
        }

    var syncSystems: Set<String>?
        get() = mmkv.decodeStringSet(KEY_SYNC_SYSTEMS)
        set(value) {
            if (value != null) {
                mmkv.encode(KEY_SYNC_SYSTEMS, value)
            } else {
                mmkv.remove(KEY_SYNC_SYSTEMS)
            }
        }

    var syncType: Int?
        get() = if (mmkv.containsKey(KEY_SYNC_TYPE)) mmkv.decodeInt(KEY_SYNC_TYPE) else null
        set(value) {
            if (value != null) {
                mmkv.encode(KEY_SYNC_TYPE, value)
            } else {
                mmkv.remove(KEY_SYNC_TYPE)
            }
        }

    fun getMMKV(): MMKV = mmkv
}

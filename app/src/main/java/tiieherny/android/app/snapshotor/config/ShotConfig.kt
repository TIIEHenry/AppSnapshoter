package tiieherny.android.app.snapshotor.config

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.TypeReference
import com.tencent.mmkv.MMKV

class ShotConfig(private val mmkv: MMKV) {
    private companion object {
        const val KEY_COMPRESS_ALGORITHM = "compressAlgorithm"
        const val KEY_COMPRESS_ITEMS = "compressItems"
        const val KEY_PERMISSION = "permission"
        const val KEY_EXTRA_ITEMS = "extraItems"
        const val KEY_AUTO_SNAPSHOT = "autoSnapshot"
        const val KEY_UNINSTALL_ARCHIVED = "uninstallArchived"
    }

    var autoSnapshot: Boolean
        get() = mmkv.decodeBool(KEY_AUTO_SNAPSHOT, false)
        set(value) {
            mmkv.encode(KEY_AUTO_SNAPSHOT, value)
        }

    var compressAlgorithm: String
        get() = mmkv.decodeString(KEY_COMPRESS_ALGORITHM) ?: ""
        set(value) {
            mmkv.encode(KEY_COMPRESS_ALGORITHM, value)
        }

    var compressItems: Set<String>
        get() = mmkv.decodeStringSet(KEY_COMPRESS_ITEMS) ?: CompressItems.all
        set(value) {
            mmkv.encode(KEY_COMPRESS_ITEMS, value)
        }

    var permission: Boolean
        get() = mmkv.decodeBool(KEY_PERMISSION, true)
        set(value) {
            mmkv.encode(KEY_PERMISSION, value)
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

    var uninstallArchived: Boolean
        get() = mmkv.decodeBool(KEY_UNINSTALL_ARCHIVED, false)
        set(value) {
            mmkv.encode(KEY_UNINSTALL_ARCHIVED, value)
        }
}
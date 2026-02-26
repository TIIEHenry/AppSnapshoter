package tiiehenry.android.app.snapshotor.config

import com.tencent.mmkv.MMKV

class SyncConfig(private val mmkv: MMKV) {
    private companion object {
        const val KEY_ENABLE_SYNC_TO_TARGET = "enableSyncToTarget"
        const val KEY_SYNC_TARGETS = "syncTargets"
        const val KEY_ENABLE_SYNC_TO_SYSTEM = "enableSyncToSystem"
        const val KEY_SYNC_SYSTEMS = "syncSystems"
        const val KEY_SYNC_TYPE = "syncType"
        const val KEY_UNINSTALL_ARCHIVED = "uninstallArchived"
    }

    var enableSyncToTarget: Boolean
        get() = mmkv.decodeBool(KEY_ENABLE_SYNC_TO_TARGET)
        set(value) {
            mmkv.encode(KEY_ENABLE_SYNC_TO_TARGET, value)
        }

    var syncTargets: Set<String>
        get() = mmkv.decodeStringSet(KEY_SYNC_TARGETS, emptySet()) ?: emptySet()
        set(value) {
            mmkv.encode(KEY_SYNC_TARGETS, value)
        }

    var enableSyncToSystem: Boolean
        get() = mmkv.decodeBool(KEY_ENABLE_SYNC_TO_SYSTEM)
        set(value) {
            mmkv.encode(KEY_ENABLE_SYNC_TO_SYSTEM, value)
        }

    var syncSystems: Set<String>
        get() = mmkv.decodeStringSet(KEY_SYNC_SYSTEMS)?:emptySet()
        set(value) {
            mmkv.encode(KEY_SYNC_SYSTEMS, value)
        }

    var syncType: Int
        get() = if (mmkv.containsKey(KEY_SYNC_TYPE)) mmkv.decodeInt(KEY_SYNC_TYPE) else 0
        set(value) {
            mmkv.encode(KEY_SYNC_TYPE, value)
        }
}
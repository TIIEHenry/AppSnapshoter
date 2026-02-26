package tiiehenry.android.app.snapshotor.config

import com.tencent.mmkv.MMKV

class GroupConfig(val groupId: String) {
    val mmkv = MMKV.mmkvWithID("group:"+groupId)

    private companion object {
        const val KEY_ROOT_PATH = "rootPath"
    }

    var rootPath: String
        get() = mmkv.decodeString(KEY_ROOT_PATH) ?: GlobalConfig.rootPath
        set(value) {
            mmkv.encode(KEY_ROOT_PATH, value)
        }

    val shotConfig = ShotConfig(mmkv)

    val syncConfig = SyncConfig(mmkv)

    val sortConfig = SortConfig(mmkv)

    fun getMMKV(): MMKV = mmkv
}

package tiiehenry.android.app.snapshotor.config

import com.tencent.mmkv.MMKV
import tiiehenry.android.app.snapshotor.SnapShotApp

object GlobalConfig {
    private const val KEY_GROUPS = "groups"
    private const val KEY_ROOT_PATH = "rootPath"
    private const val KEY_COMPRESS_ALGORITHM = "compressAlgorithm"
    private const val KEY_FILE_SYSTEM_PROVIDER = "fileSystemProvider"
    private const val KEY_APP_MANAGER_PROVIDER = "appManagerProvider"
    private const val KEY_DATA_SYNC_PROVIDER = "dataSyncProvider"
    private const val KEY_SYNC_SYSTEMS = "syncSystems"

    private val mmkv: MMKV
        get() = MMKV.defaultMMKV()

    var groups: Set<String>
        get() = mmkv.decodeStringSet(KEY_GROUPS, emptySet()) ?: emptySet()
        set(value) {
            mmkv.encode(KEY_GROUPS, value)
        }

    var rootPath: String
        get() = mmkv.decodeString(KEY_ROOT_PATH, SnapShotApp.getInstance().defaultRootPath)
            ?: SnapShotApp.getInstance().defaultRootPath
        set(value) {
            mmkv.encode(KEY_ROOT_PATH, value)
        }

    val shotConfig = ShotConfig(mmkv)

    val syncConfig = SyncConfig(mmkv)

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

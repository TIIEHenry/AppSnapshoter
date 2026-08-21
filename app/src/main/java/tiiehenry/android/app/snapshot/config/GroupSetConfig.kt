package tiiehenry.android.app.snapshot.config

import com.tencent.mmkv.MMKV
import tiiehenry.android.app.snapshot.SnapshotApp
import java.io.File

/**
 * 分组集本机配置（MMKV）+ 可同步 [groupset.json]。
 */
class GroupSetConfig(val setId: String) {
    companion object {
        const val KEY_ROOT_PATH = "rootPath"
        const val KEY_IS_COLLAPSED = "isCollapsed"
    }

    val mmkv = MMKV.mmkvWithID("groupset:$setId")

    var rootPath: String
        get() = mmkv.decodeString(KEY_ROOT_PATH) ?: SnapshotApp.getInstance().globalRootPath
        set(value) {
            mmkv.encode(KEY_ROOT_PATH, value)
        }

    /** 新建默认折叠 */
    var isCollapsed: Boolean
        get() = mmkv.decodeBool(KEY_IS_COLLAPSED, true)
        set(value) {
            mmkv.encode(KEY_IS_COLLAPSED, value)
        }

    private val configFile get() = File(rootPath, ConfigFiles.GROUP_SET_CONFIG_FILE)

    var data: GroupSetConfigData = GroupSetConfigData()

    init {
        load()
    }

    fun load() {
        data = loadConfigFromFile(configFile) { GroupSetConfigData.fromJson(it) }
            ?: GroupSetConfigData()
    }

    fun save() {
        saveConfigToFile(configFile, data.toJson())
    }

    fun clearLocal() {
        mmkv.clearAll()
    }

    private fun <T> loadConfigFromFile(file: File, parser: (String) -> T): T? {
        return try {
            if (!file.exists()) return null
            parser(file.readText())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveConfigToFile(file: File, content: String) {
        try {
            file.parentFile?.mkdirs()
            file.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

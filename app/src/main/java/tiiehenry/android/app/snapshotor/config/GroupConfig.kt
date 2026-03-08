package tiiehenry.android.app.snapshotor.config

import com.tencent.mmkv.MMKV
import java.io.File

/**
 * 分组配置管理类
 * 配置保存在 rootPath 目录下的 JSON 文件中
 */
class GroupConfig(val groupId: String) {
    companion object {
        const val KEY_ROOT_PATH = "rootPath"
        private const val SHOT_CONFIG_FILE = "shot_config.json"
        private const val SORT_CONFIG_FILE = "sort_config.json"
        private const val GROUP_CONFIG_FILE = "group.json"
    }

    val mmkv = MMKV.mmkvWithID("group:" + groupId)

    fun getMMKV(): MMKV = mmkv

    var rootPath: String
        get() = mmkv.decodeString(KEY_ROOT_PATH) ?: GlobalConfig.rootPath
        set(value) {
            mmkv.encode(KEY_ROOT_PATH, value)
        }

    private val shotConfigFile by lazy { File(rootPath, SHOT_CONFIG_FILE) }
    private val sortConfigFile by lazy { File(rootPath, SORT_CONFIG_FILE) }
    private val groupConfigFile by lazy { File(rootPath, GROUP_CONFIG_FILE) }

    // 配置对象
    var shotConfig: ShotConfig = ShotConfig()
        private set

    var sortConfig: SortConfig = SortConfig()
        private set

    var groupConfigData: GroupConfigData = GroupConfigData()
        private set

    init {
        load()
    }

    /**
     * 从文件加载配置
     */
    fun load() {
        shotConfig = loadConfigFromFile(shotConfigFile) { ShotConfig.fromJson(it) } ?: ShotConfig()
        sortConfig = loadConfigFromFile(sortConfigFile) { SortConfig.fromJson(it) } ?: SortConfig()
        groupConfigData = loadConfigFromFile(groupConfigFile) { GroupConfigData.fromJson(it) } ?: GroupConfigData()
    }

    /**
     * 保存配置到文件
     */
    fun save() {
        saveConfigToFile(shotConfigFile, shotConfig.toJson())
        saveConfigToFile(sortConfigFile, sortConfig.toJson())
        saveConfigToFile(groupConfigFile, groupConfigData.toJson())
    }

    /**
     * 重置配置
     */
    fun reset() {
        shotConfig = ShotConfig()
        sortConfig = SortConfig()
        groupConfigData = GroupConfigData()
        shotConfigFile.delete()
        sortConfigFile.delete()
        groupConfigFile.delete()
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

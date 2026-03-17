package tiiehenry.android.app.snapshot.config

import com.tencent.mmkv.MMKV
import tiiehenry.android.app.snapshot.SnapshotApp
import java.io.File

/**
 * 分组配置管理类
 * 配置保存在 rootPath 目录下的 JSON 文件中
 */
class GroupConfig(val groupId: String) {
    companion object {
        const val KEY_ROOT_PATH = "rootPath"
    }

    val mmkv = MMKV.mmkvWithID("group:" + groupId)

    var rootPath: String
        get() = mmkv.decodeString(KEY_ROOT_PATH) ?: SnapshotApp.getInstance().globalRootPath
        set(value) {
            mmkv.encode(KEY_ROOT_PATH, value)
        }

    private val shotConfigFile get() = File(rootPath, ConfigFiles.SHOT_CONFIG_FILE)
    private val excludeConfigFile get() = File(rootPath, ConfigFiles.EXCLUDE_CONFIG_FILE)
    private val actionConfigFile get() = File(rootPath, ConfigFiles.ACTION_CONFIG_FILE)
    private val groupConfigFile get() = File(rootPath, ConfigFiles.GROUP_CONFIG_FILE)

    // 配置对象
    var shotConfig: ShotConfig = ShotConfig()
        private set

    var excludeConfig: ExcludeConfig = ExcludeConfig()
        private set

    var actionConfig: ActionConfig = ActionConfig()
        private set

    var sortConfig: SortConfig
        get() = groupConfigData.sortConfig
        set(value) { groupConfigData.sortConfig = value }

    var groupConfigData: GroupConfigData = GroupConfigData()

    init {
        load()
    }

    /**
     * 从文件加载配置
     */
    fun load() {
        shotConfig = loadConfigFromFile(shotConfigFile) { ShotConfig.fromJson(it) } ?: ShotConfig()
        excludeConfig = loadConfigFromFile(excludeConfigFile) { ExcludeConfig.fromJson(it) } ?: ExcludeConfig()
        actionConfig = loadConfigFromFile(actionConfigFile) { ActionConfig.fromJson(it) } ?: ActionConfig()
        groupConfigData = loadConfigFromFile(groupConfigFile) { GroupConfigData.fromJson(it) }
            ?: GroupConfigData()
    }

    /**
     * 保存配置到文件
     */
    fun save() {
        saveConfigToFile(shotConfigFile, shotConfig.toJson())
        saveConfigToFile(excludeConfigFile, excludeConfig.toJson())
        saveConfigToFile(actionConfigFile, actionConfig.toJson())
        saveConfigToFile(groupConfigFile, groupConfigData.toJson())
    }

    /**
     * 重置配置
     */
    fun reset() {
        shotConfig = ShotConfig()
        excludeConfig = ExcludeConfig()
        actionConfig = ActionConfig()
        groupConfigData = GroupConfigData()
        shotConfigFile.delete()
        excludeConfigFile.delete()
        actionConfigFile.delete()
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

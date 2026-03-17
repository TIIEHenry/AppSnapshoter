package tiiehenry.android.app.snapshot.config

import tiiehenry.android.app.snapshot.SnapshotApp
import java.io.File

/**
 * 应用配置管理类
 * 配置保存在应用私有目录下的 JSON 文件中
 */
class AppConfig(val packageName: String) {

    // 获取应用配置存储目录（私有目录下的 app_configs/<packageName>/）
    private val configDir: String by lazy {
        File(SnapshotApp.getInstance().globalRootPath, "app_configs/$packageName").absolutePath
    }

    private val shotConfigFile by lazy { File(configDir, ConfigFiles.SHOT_CONFIG_FILE) }
    private val excludeConfigFile by lazy { File(configDir, ConfigFiles.EXCLUDE_CONFIG_FILE) }
    private val actionConfigFile by lazy { File(configDir, ConfigFiles.ACTION_CONFIG_FILE) }
    private val extraConfigFile by lazy { File(configDir, ConfigFiles.EXTRA_CONFIG_FILE) }

    // 配置对象
    var shotConfig: ShotConfig = ShotConfig()
        private set

    var excludeConfig: ExcludeConfig = ExcludeConfig()
        private set

    var actionConfig: ActionConfig = ActionConfig()

    private var extraItemsConfig: ExtraItemsConfig = ExtraItemsConfig()

    /**
     * 获取额外压缩项目列表
     */
    val extraItems: List<ExtraCompressItem>
        get() = extraItemsConfig.getItems()

    /**
     * 获取动作配置
     */
    val action: ActionConfig
        get() = actionConfig

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
        extraItemsConfig = loadConfigFromFile(extraConfigFile) { ExtraItemsConfig.fromJson(it) } ?: ExtraItemsConfig()
    }

    /**
     * 保存配置到文件
     */
    fun save() {
        saveConfigToFile(shotConfigFile, shotConfig.toJson())
        saveConfigToFile(excludeConfigFile, excludeConfig.toJson())
        saveConfigToFile(actionConfigFile, actionConfig.toJson())
        saveConfigToFile(extraConfigFile, extraItemsConfig.toJson())
    }

    /**
     * 保存额外项目列表
     */
    fun saveExtraItems(items: List<ExtraCompressItem>) {
        extraItemsConfig.setItems(items)
        saveConfigToFile(extraConfigFile, extraItemsConfig.toJson())
    }

    /**
     * 重置配置
     */
    fun reset() {
        shotConfig = ShotConfig()
        excludeConfig = ExcludeConfig()
        actionConfig = ActionConfig()
        extraItemsConfig = ExtraItemsConfig()
        shotConfigFile.delete()
        excludeConfigFile.delete()
        actionConfigFile.delete()
        extraConfigFile.delete()
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

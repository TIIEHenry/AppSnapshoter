package tiiehenry.android.app.snapshot.config

import tiiehenry.android.app.snapshot.SnapshotApp
import java.io.File

/**
 * 应用配置管理类
 * 配置保存在应用私有目录下的 JSON 文件中
 */
class AppConfig(val packageName: String, val userId: Int = 0) {

    private fun canonicalConfigDir(): File {
        return File(
            SnapshotApp.getInstance().globalRootPath,
            "app_configs/${AppConfigManager.configKey(packageName, userId)}"
        )
    }

    // 获取应用配置存储目录；user 0 兼容旧版仅按包名的目录
    private val configDir: String by lazy {
        val canonical = canonicalConfigDir()
        if (userId == 0) {
            val legacy = File(SnapshotApp.getInstance().globalRootPath, "app_configs/$packageName")
            if (!canonical.exists() && legacy.exists()) legacy.absolutePath else canonical.absolutePath
        } else {
            canonical.absolutePath
        }
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
     * 保存配置到文件（始终写入规范路径，便于从旧版目录迁移）
     */
    fun save() {
        val targetDir = canonicalConfigDir()
        targetDir.mkdirs()
        saveConfigToFile(File(targetDir, ConfigFiles.SHOT_CONFIG_FILE), shotConfig.toJson())
        saveConfigToFile(File(targetDir, ConfigFiles.EXCLUDE_CONFIG_FILE), excludeConfig.toJson())
        saveConfigToFile(File(targetDir, ConfigFiles.ACTION_CONFIG_FILE), actionConfig.toJson())
        saveConfigToFile(File(targetDir, ConfigFiles.EXTRA_CONFIG_FILE), extraItemsConfig.toJson())
    }

    /**
     * 保存额外项目列表
     */
    fun saveExtraItems(items: List<ExtraCompressItem>) {
        extraItemsConfig.setItems(items)
        val targetDir = canonicalConfigDir()
        targetDir.mkdirs()
        saveConfigToFile(File(targetDir, ConfigFiles.EXTRA_CONFIG_FILE), extraItemsConfig.toJson())
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
        canonicalConfigDir().deleteRecursively()
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

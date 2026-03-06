package tiiehenry.android.app.snapshotor.config

import tiiehenry.android.app.snapshotor.SnapShotApp
import java.io.File

/**
 * 应用配置管理类
 * 配置保存在应用私有目录下的 JSON 文件中
 */
class AppConfig(val packageName: String) {
    companion object {
        private const val SHOT_CONFIG_FILE = "shot_config.json"
        private const val SYNC_CONFIG_FILE = "sync_config.json"
    }

    // 获取应用配置存储目录（私有目录下的 app_configs/<packageName>/）
    private val configDir: String by lazy {
        File(SnapShotApp.getInstance().defaultRootPath, "app_configs/$packageName").absolutePath
    }

    private val shotConfigFile by lazy { File(configDir, SHOT_CONFIG_FILE) }
    private val syncConfigFile by lazy { File(configDir, SYNC_CONFIG_FILE) }

    // 配置对象
    var shotConfig: ShotConfig = ShotConfig()
        private set

    var syncConfig: SyncConfig = SyncConfig()
        private set

    init {
        load()
    }

    /**
     * 从文件加载配置
     */
    fun load() {
        shotConfig = loadConfigFromFile(shotConfigFile) { ShotConfig.fromJson(it) } ?: ShotConfig()
        syncConfig = loadConfigFromFile(syncConfigFile) { SyncConfig.fromJson(it) } ?: SyncConfig()
    }

    /**
     * 保存配置到文件
     */
    fun save() {
        saveConfigToFile(shotConfigFile, shotConfig.toJson())
        saveConfigToFile(syncConfigFile, syncConfig.toJson())
    }

    /**
     * 重置配置
     */
    fun reset() {
        shotConfig = ShotConfig()
        syncConfig = SyncConfig()
        shotConfigFile.delete()
        syncConfigFile.delete()
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

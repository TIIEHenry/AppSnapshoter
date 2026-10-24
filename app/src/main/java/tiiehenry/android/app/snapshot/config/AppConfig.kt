package tiiehenry.android.app.snapshot.config

import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * 应用配置管理类
 * 配置保存在应用私有目录下的 JSON 文件中
 *
 * user 0 的读写目录每次按当前全局根计算。规范目录缺哪份文件、旧目录有哪份，就把缺的那几份补过去，
 * 再删旧目录；复制没完成就留着旧目录，并继续从旧目录读。规范目录里已经有的文件不动。
 */
class AppConfig(val packageName: String, val userId: Int = 0) {

    private val configFileNames = listOf(
        ConfigFiles.SHOT_CONFIG_FILE,
        ConfigFiles.EXCLUDE_CONFIG_FILE,
        ConfigFiles.ACTION_CONFIG_FILE,
        ConfigFiles.EXTRA_CONFIG_FILE,
    )

    // 配置对象
    var shotConfig: ShotConfig = ShotConfig()
        private set

    var excludeConfig: ExcludeConfig = ExcludeConfig()
        private set

    var actionConfig: ActionConfig = ActionConfig()

    private var extraItemsConfig: ExtraItemsConfig = ExtraItemsConfig()

    private var readFailed = false
    private var loadedRoot: String? = null

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

    private fun canonicalConfigDir(): File {
        return File(
            ConfigEnvironment.globalRootPath(),
            "app_configs/${AppConfigManager.configKey(packageName, userId)}",
        )
    }

    private fun legacyConfigDir(): File {
        return File(ConfigEnvironment.globalRootPath(), "app_configs/$packageName")
    }

    /** 旧目录还有规范目录里没有的配置文件时，继续从旧目录读。 */
    private fun legacyReadRequired(): Boolean {
        if (userId != 0) return false
        val legacy = legacyConfigDir()
        val canonical = canonicalConfigDir()
        return configFileNames.any { name ->
            File(legacy, name).isFile && !File(canonical, name).isFile
        }
    }

    private fun configDir(): File {
        if (legacyReadRequired()) return legacyConfigDir()
        return canonicalConfigDir()
    }

    /**
     * 只补规范目录里还不存在的那几份。按文件是否存在判断，不按目录是否存在。
     * 复制中途失败则保留旧目录。
     */
    private fun migrateLegacyConfigs() {
        if (userId != 0) return
        val legacy = legacyConfigDir()
        if (!legacy.isDirectory) return
        val canonical = canonicalConfigDir()
        val pending = configFileNames.filter { name ->
            File(legacy, name).isFile && !File(canonical, name).isFile
        }
        if (pending.isNotEmpty()) {
            if (!canonical.isDirectory && !canonical.mkdirs()) return
            for (name in pending) {
                val target = File(canonical, name)
                if (target.exists()) continue
                try {
                    Files.copy(File(legacy, name).toPath(), target.toPath())
                } catch (e: Exception) {
                    e.printStackTrace()
                    return
                }
            }
            if (configFileNames.any { name ->
                    File(legacy, name).isFile && !File(canonical, name).isFile
                }
            ) {
                return
            }
        }
        if (configFileNames.any { name -> File(legacy, name).isFile }) {
            legacy.deleteRecursively()
        }
    }

    /**
     * 从文件加载配置
     */
    fun load() {
        migrateLegacyConfigs()
        val dir = configDir()
        val shot = ConfigFileStore.read(File(dir, ConfigFiles.SHOT_CONFIG_FILE)) { ShotConfig.fromJson(it) }
        val exclude = ConfigFileStore.read(File(dir, ConfigFiles.EXCLUDE_CONFIG_FILE)) {
            ExcludeConfig.fromJson(it)
        }
        val actionRead = ConfigFileStore.read(File(dir, ConfigFiles.ACTION_CONFIG_FILE)) {
            ActionConfig.fromJson(it)
        }
        val extra = ConfigFileStore.read(File(dir, ConfigFiles.EXTRA_CONFIG_FILE)) {
            ExtraItemsConfig.fromJson(it)
        }
        shotConfig = shot.value ?: ShotConfig()
        excludeConfig = exclude.value ?: ExcludeConfig()
        actionConfig = actionRead.value ?: ActionConfig()
        extraItemsConfig = extra.value ?: ExtraItemsConfig()
        readFailed = shot.failed || exclude.failed || actionRead.failed || extra.failed
        loadedRoot = ConfigEnvironment.globalRootPath()
    }

    /**
     * 保存配置到当前全局根下的活动目录。
     * 全局根变了就先按新根重新加载，避免用旧根内存盖掉新根。
     */
    fun save() {
        if (ConfigEnvironment.globalRootPath() != loadedRoot) {
            load()
        }
        if (readFailed) {
            throw IllegalStateException(
                "Refusing to save app config for $packageName@$userId after a read failure",
            )
        }
        migrateLegacyConfigs()
        val dir = configDir()
        if (!dir.isDirectory && !dir.mkdirs()) {
            throw IOException("Failed to create ${dir.path}")
        }
        ConfigFileStore.write(File(dir, ConfigFiles.SHOT_CONFIG_FILE), shotConfig.toJson())
        ConfigFileStore.write(File(dir, ConfigFiles.EXCLUDE_CONFIG_FILE), excludeConfig.toJson())
        ConfigFileStore.write(File(dir, ConfigFiles.ACTION_CONFIG_FILE), actionConfig.toJson())
        ConfigFileStore.write(File(dir, ConfigFiles.EXTRA_CONFIG_FILE), extraItemsConfig.toJson())
    }

    /**
     * 保存额外项目列表。迁移在建目录和写 extra.json 之前完成。
     */
    fun saveExtraItems(items: List<ExtraCompressItem>) {
        if (ConfigEnvironment.globalRootPath() != loadedRoot) {
            load()
        }
        if (readFailed) {
            throw IllegalStateException(
                "Refusing to save app config for $packageName@$userId after a read failure",
            )
        }
        extraItemsConfig.setItems(items)
        migrateLegacyConfigs()
        val dir = configDir()
        if (!dir.isDirectory && !dir.mkdirs()) {
            throw IOException("Failed to create ${dir.path}")
        }
        ConfigFileStore.write(File(dir, ConfigFiles.EXTRA_CONFIG_FILE), extraItemsConfig.toJson())
    }

    /**
     * 重置配置。规范目录和 user 0 的旧目录一起删掉。
     */
    fun reset() {
        shotConfig = ShotConfig()
        excludeConfig = ExcludeConfig()
        actionConfig = ActionConfig()
        extraItemsConfig = ExtraItemsConfig()
        readFailed = false
        deleteConfigTree(canonicalConfigDir())
        if (userId == 0) {
            deleteConfigTree(legacyConfigDir())
        }
        loadedRoot = ConfigEnvironment.globalRootPath()
    }

    private fun deleteConfigTree(dir: File) {
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }
}

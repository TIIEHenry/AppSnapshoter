package tiiehenry.android.app.snapshot.config

import com.tencent.mmkv.MMKV
import java.io.File

/**
 * 分组集本机配置（MMKV）+ 可同步 [groupset.json]。
 */
class GroupSetConfig internal constructor(
    val setId: String,
    private val prefs: ConfigPrefs,
) {
    constructor(setId: String) : this(
        setId,
        MmkvConfigPrefs(MMKV.mmkvWithID("groupset:$setId")),
    )

    companion object {
        const val KEY_ROOT_PATH = "rootPath"
        const val KEY_IS_COLLAPSED = "isCollapsed"
        const val KEY_ACCENT_COLOR = "accentColor"
    }

    val mmkv: MMKV
        get() = (prefs as MmkvConfigPrefs).mmkv

    /**
     * 空字符串和空白按未设置，回退当前全局根。
     * 路径变了就清掉强调色键并重载目标目录，不在这里保存。
     */
    var rootPath: String
        get() {
            val stored = prefs.decodeString(KEY_ROOT_PATH)
            if (stored.isNullOrBlank()) return ConfigEnvironment.globalRootPath()
            return stored
        }
        set(value) {
            val before = rootPath
            prefs.encode(KEY_ROOT_PATH, value)
            if (rootPath != before) {
                prefs.removeValueForKey(KEY_ACCENT_COLOR)
                load()
            }
        }

    /** 新建默认折叠 */
    var isCollapsed: Boolean
        get() = prefs.decodeBool(KEY_IS_COLLAPSED, true)
        set(value) {
            prefs.encode(KEY_IS_COLLAPSED, value)
        }

    /**
     * 强调色：MMKV 优先 → groupset.json → 按 setId 默认。
     * 写入时双写 MMKV 与 [GroupSetConfigData.accentColor]。
     */
    var accentColor: Int
        get() {
            if (prefs.containsKey(KEY_ACCENT_COLOR)) {
                return prefs.decodeInt(KEY_ACCENT_COLOR, ConfigEnvironment.defaultAccentColor(setId))
            }
            ConfigEnvironment.parseAccentColor(data.accentColor)?.let { return it }
            return ConfigEnvironment.defaultAccentColor(setId)
        }
        set(value) {
            prefs.encode(KEY_ACCENT_COLOR, value)
            data.accentColor = ConfigEnvironment.formatAccentColor(value)
        }

    private val configFile get() = File(rootPath, ConfigFiles.GROUP_SET_CONFIG_FILE)

    var data: GroupSetConfigData = GroupSetConfigData()

    private var readFailed = false
    private var cleared = false
    private var loadedPath: String? = null

    init {
        load()
    }

    fun load() {
        cleared = false
        val path = rootPath
        val result = ConfigFileStore.read(configFile) { GroupSetConfigData.fromJson(it) }
        readFailed = result.failed
        data = result.value ?: GroupSetConfigData()
        loadedPath = path
        if (readFailed) return
        if (!prefs.containsKey(KEY_ACCENT_COLOR)) {
            ConfigEnvironment.parseAccentColor(data.accentColor)?.let { parsed ->
                prefs.encode(KEY_ACCENT_COLOR, parsed)
            }
        }
    }

    fun save() {
        if (cleared) {
            throw IllegalStateException("Refusing to save cleared group set $setId before reload")
        }
        if (rootPath != loadedPath) {
            load()
        }
        if (cleared || readFailed) {
            throw IllegalStateException("Refusing to save group set $setId after a read failure")
        }
        ConfigFileStore.write(configFile, data.toJson())
    }

    fun clearLocal() {
        prefs.clearAll()
        data = GroupSetConfigData()
        cleared = true
        readFailed = false
        loadedPath = null
    }
}

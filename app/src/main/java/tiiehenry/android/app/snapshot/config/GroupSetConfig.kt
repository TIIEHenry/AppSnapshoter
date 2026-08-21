package tiiehenry.android.app.snapshot.config

import com.tencent.mmkv.MMKV
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.group.GroupSetColors
import java.io.File

/**
 * 分组集本机配置（MMKV）+ 可同步 [groupset.json]。
 */
class GroupSetConfig(val setId: String) {
    companion object {
        const val KEY_ROOT_PATH = "rootPath"
        const val KEY_IS_COLLAPSED = "isCollapsed"
        const val KEY_ACCENT_COLOR = "accentColor"
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

    /**
     * 强调色：MMKV 优先 → groupset.json → 按 setId 默认。
     * 写入时双写 MMKV 与 [GroupSetConfigData.accentColor]。
     */
    var accentColor: Int
        get() {
            if (mmkv.containsKey(KEY_ACCENT_COLOR)) {
                return mmkv.decodeInt(KEY_ACCENT_COLOR, GroupSetColors.defaultFor(setId))
            }
            GroupSetColors.parseHex(data.accentColor)?.let { return it }
            return GroupSetColors.defaultFor(setId)
        }
        set(value) {
            mmkv.encode(KEY_ACCENT_COLOR, value)
            data.accentColor = GroupSetColors.toHex(value)
        }

    private val configFile get() = File(rootPath, ConfigFiles.GROUP_SET_CONFIG_FILE)

    var data: GroupSetConfigData = GroupSetConfigData()

    init {
        load()
    }

    fun load() {
        data = loadConfigFromFile(configFile) { GroupSetConfigData.fromJson(it) }
            ?: GroupSetConfigData()
        if (!mmkv.containsKey(KEY_ACCENT_COLOR)) {
            GroupSetColors.parseHex(data.accentColor)?.let {
                mmkv.encode(KEY_ACCENT_COLOR, it)
            }
        }
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

package tiiehenry.android.app.snapshot.config

import com.tencent.mmkv.MMKV
import tiiehenry.android.app.snapshot.group.GroupMembershipMode
import java.io.File

/**
 * 分组配置管理类
 * 配置保存在 rootPath 目录下的 JSON 文件中
 */
class GroupConfig internal constructor(
    val groupId: String,
    private val prefs: ConfigPrefs,
) {
    constructor(groupId: String) : this(
        groupId,
        MmkvConfigPrefs(MMKV.mmkvWithID("group:$groupId")),
    )

    companion object {
        const val KEY_ROOT_PATH = "rootPath"
    }

    val mmkv: MMKV
        get() = (prefs as MmkvConfigPrefs).mmkv

    /**
     * 空字符串和空白按未设置，回退当前全局根。
     * 变更后只重载目标目录，不在这里保存。
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
                load()
            }
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

    /** 只读；写入请走 [tiiehenry.android.app.snapshot.repository.AppDataRepository.setMembershipMode]。 */
    val membershipMode: GroupMembershipMode
        get() = GroupMembershipMode.fromStorage(groupConfigData.membershipMode)

    val isExclusive: Boolean
        get() = membershipMode == GroupMembershipMode.EXCLUSIVE

    private var readFailed = false
    private var loadedPath: String? = null

    init {
        load()
    }

    /**
     * 从文件加载配置
     */
    fun load() {
        val path = rootPath
        val state = GroupConfigState.load(path)
        applyState(state)
        readFailed = state.readFailed
        loadedPath = path
    }

    /** Register an existing directory without replacing its settings with defaults. */
    fun registerPath(path: String, name: String, userId: Int) {
        rootPath = path
        val state = GroupConfigState.forRegistration(rootPath, name, userId)
        applyState(state)
        readFailed = state.readFailed
        if (readFailed) {
            throw IllegalStateException("Refusing to save group $groupId after a read failure")
        }
        save()
    }

    private fun applyState(state: GroupConfigState) {
        shotConfig = state.shot
        excludeConfig = state.exclude
        actionConfig = state.action
        groupConfigData = state.group
    }

    /**
     * 保存配置到文件。路径与上次加载不一致时，先装入目标目录，不用旧内存覆盖它。
     */
    fun save() {
        if (rootPath != loadedPath) {
            load()
        }
        if (readFailed) {
            throw IllegalStateException("Refusing to save group $groupId after a read failure")
        }
        ConfigFileStore.write(shotConfigFile, shotConfig.toJson())
        ConfigFileStore.write(excludeConfigFile, excludeConfig.toJson())
        ConfigFileStore.write(actionConfigFile, actionConfig.toJson())
        ConfigFileStore.write(groupConfigFile, groupConfigData.toJson())
    }

    /**
     * 重置配置
     */
    fun reset() {
        shotConfig = ShotConfig()
        excludeConfig = ExcludeConfig()
        actionConfig = ActionConfig()
        groupConfigData = GroupConfigData()
        readFailed = false
        loadedPath = rootPath
        shotConfigFile.delete()
        excludeConfigFile.delete()
        actionConfigFile.delete()
        groupConfigFile.delete()
    }

    /**
     * 添加应用到锁定列表
     */
    fun addToLockedList(packageName: String) {
        if (rootPath != loadedPath) {
            load()
        }
        if (readFailed) {
            throw IllegalStateException("Refusing to save group $groupId after a read failure")
        }
        if (!groupConfigData.lockedList.contains(packageName)) {
            groupConfigData.lockedList.add(packageName)
            save()
        }
    }

    /**
     * 从锁定列表中移除应用
     */
    fun removeFromLockedList(packageName: String) {
        if (rootPath != loadedPath) {
            load()
        }
        if (readFailed) {
            throw IllegalStateException("Refusing to save group $groupId after a read failure")
        }
        if (groupConfigData.lockedList.contains(packageName)) {
            groupConfigData.lockedList.remove(packageName)
            save()
        }
    }

    /**
     * 检查应用是否在锁定列表中
     */
    fun isLocked(packageName: String): Boolean {
        return groupConfigData.lockedList.contains(packageName)
    }
}

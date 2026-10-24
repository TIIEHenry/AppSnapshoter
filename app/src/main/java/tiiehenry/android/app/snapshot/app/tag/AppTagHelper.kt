package tiiehenry.android.app.snapshot.app.tag

import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.config.GlobalConfig
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.snapshot.fs.IFileType

/**
 * 应用标签帮助类
 * 用于动态解析应用的标签
 */
object AppTagHelper {

    // 缓存：应用包名到标签列表的映射
    private val appTagsCache = mutableMapOf<String, List<AppTag>>()

    internal var groupListProvider: () -> List<SnapGroup>? = {
        SnapshotApp.getViewModel().groupList.value
    }

    internal var fileTypeProvider: (String) -> Int = { path ->
        SnapshotApp.getInstance().fileSystem.fileType(path)
    }

    internal fun resetProviders() {
        groupListProvider = { SnapshotApp.getViewModel().groupList.value }
        fileTypeProvider = { path -> SnapshotApp.getInstance().fileSystem.fileType(path) }
    }

    /**
     * 清除标签缓存
     */
    fun clearCache() {
        appTagsCache.clear()
    }

    /**
     * 获取应用的所有标签（带缓存）
     * 包括：system/user/xposed + 所属分组的名称
     */
    fun getAppTags(appInfo: AppInfo): List<AppTag> {
        val cacheKey = appInfo.packageName
        return appTagsCache.getOrPut(cacheKey) {
            computeAppTags(appInfo)
        }
    }

    /**
     * 计算应用的所有标签（无缓存）
     */
    private fun computeAppTags(appInfo: AppInfo): List<AppTag> {
        val tags = mutableListOf<AppTag>()

        // 检查是否是Xposed模块 - 使用lazy缓存的结果
        if (appInfo.isXposedModule) {
            tags.add(AppTag.XPOSED_TAG)
        }

        return tags
    }

    /**
     * 检查应用是否在指定分组中
     */
    fun isAppInGroup(appInfo: AppInfo, group: SnapGroup): Boolean =
        isAppInGroup(appInfo, group, fileTypeProvider)

    internal fun isAppInGroup(
        appInfo: AppInfo,
        group: SnapGroup,
        fileType: (String) -> Int,
    ): Boolean {
        if (appInfo.userId != group.userId) return false
        val appDir = "${group.path}/${appInfo.packageName}"
        return fileType(appDir) == IFileType.TYPE_DIR
    }

    /**
     * 获取所有可用的标签（用于标签过滤器）
     * 包括所有内置标签和所有分组标签
     */
    fun getAllAvailableTags(): List<AppTag> {
        val tags = mutableListOf<AppTag>()

        // 添加内置标签
        tags.add(AppTag.XPOSED_TAG)

        // 使用已加载的分组对象，避免新建 SnapGroup 时 name 等字段未初始化
        val loadedGroups = SnapshotApp.getViewModel().groupList.value
        val groupIds = GlobalConfig.groups
        for (groupId in groupIds) {
            val groupName = loadedGroups?.find { it.id == groupId }?.name
                ?: groupId
            tags.add(
                AppTag(
                    id = "group_$groupId",
                    name = groupName,
                    type = TagType.GROUP
                )
            )
        }

        return tags
    }

    /**
     * 根据标签ID列表过滤应用
     * @param apps 应用列表
     * @param selectedTagIds 选中的标签ID列表，为空表示不过滤
     * @return 过滤后的应用列表
     */
    fun filterAppsByTags(apps: List<AppInfo>, selectedTagIds: Set<String>): List<AppInfo> {
        val groupTagIds = selectedTagIds.filter { it != AppTag.TAG_XPOSED }.toSet()
        val groups = if (groupTagIds.isEmpty()) emptyList() else groupListProvider()
        return filterAppsByTags(apps, selectedTagIds, groups, fileTypeProvider)
    }

    fun filterAppsByTags(
        apps: List<AppInfo>,
        selectedTagIds: Set<String>,
        groups: List<SnapGroup>?,
        fileType: (String) -> Int,
    ): List<AppInfo> {
        if (selectedTagIds.isEmpty()) {
            return apps
        }

        val builtinTagIds = selectedTagIds.filter {
            it == AppTag.TAG_XPOSED
        }.toSet()
        val groupTagIds = selectedTagIds - builtinTagIds
        if (groupTagIds.isEmpty()) {
            return apps.filter { appInfo ->
                val appTags = getAppTags(appInfo).map { it.id }
                builtinTagIds.all { it in appTags }
            }
        }

        val groupList = groups.orEmpty()
        val resolvedGroups = ArrayList<SnapGroup>(groupTagIds.size)
        var unresolved = false
        for (groupTagId in groupTagIds) {
            val groupId = groupTagId.removePrefix("group_")
            val group = groupList.find { it.id == groupId }
            if (group == null) unresolved = true else resolvedGroups.add(group)
        }

        return apps.filter { appInfo ->
            val appTags = getAppTags(appInfo).map { it.id }
            val builtinMatch = builtinTagIds.all { it in appTags }
            builtinMatch && !unresolved && resolvedGroups.all { group ->
                isAppInGroup(appInfo, group, fileType)
            }
        }
    }
}

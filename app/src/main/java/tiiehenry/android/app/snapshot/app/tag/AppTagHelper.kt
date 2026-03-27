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
    fun isAppInGroup(appInfo: AppInfo, group: SnapGroup): Boolean {
        // 通过检查分组路径下是否存在该应用的目录来判断
        val fs = SnapshotApp.getInstance().fileSystem
        val appDir = "${group.path}/${appInfo.packageName}"
        return fs.fileType(appDir) == IFileType.TYPE_DIR
    }

    /**
     * 获取所有可用的标签（用于标签过滤器）
     * 包括所有内置标签和所有分组标签
     */
    fun getAllAvailableTags(): List<AppTag> {
        val tags = mutableListOf<AppTag>()

        // 添加内置标签
        tags.add(AppTag.XPOSED_TAG)

        // 添加所有分组标签
        val groups = GlobalConfig.groups
        for (groupId in groups) {
            val group = SnapGroup(groupId)
            tags.add(
                AppTag(
                    id = "group_$groupId",
                    name = group.name,
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
        if (selectedTagIds.isEmpty()) {
            return apps
        }

        // 分离内置标签和分组标签
        val builtinTagIds = selectedTagIds.filter { 
            it == AppTag.TAG_XPOSED 
        }.toSet()
        
        val groupTagIds = selectedTagIds - builtinTagIds
        
        // 从ViewModel获取已有的分组对象，避免重复创建
        val viewModel = SnapshotApp.getViewModel()
        val groupsToCheck: List<SnapGroup> = groupTagIds.mapNotNull { groupTagId ->
            val groupId = groupTagId.removePrefix("group_")
            viewModel.groupList.value?.find { group -> group.id == groupId }
        }


        return apps.filter { appInfo ->
            // 获取应用的标签（使用缓存）
            val appTags = getAppTags(appInfo).map { it.id }
            
            // 检查内置标签是否都匹配
            val builtinMatch = builtinTagIds.all { it in appTags }
            
            // 如果没有分组标签过滤，直接返回内置标签匹配结果
            if (groupsToCheck.isEmpty()) {
                builtinMatch
            } else {
                // 需要检查分组标签 - 这个比较耗时，只在必要时执行
                builtinMatch && groupsToCheck.all { group ->
                    isAppInGroup(appInfo, group)
                }
            }
        }
    }
}

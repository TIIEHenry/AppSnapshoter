package tiiehenry.android.app.snapshot.main.apps

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.app.AppFilterHelper
import tiiehenry.android.app.snapshot.app.AppFilterType
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.app.tag.AppTag
import tiiehenry.android.app.snapshot.app.tag.AppTagHelper
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.snapshot.app.UserInfoParcelable

class AppsViewModel : ViewModel() {

    val filteredAppList = MutableLiveData<List<AppInfo>>()
    private var appsMap: Map<Int, List<AppInfo>> = emptyMap()
    private var currentQuery: String = ""
    private var currentFilterType: Set<AppFilterType> = setOf(AppFilterType.SYSTEM, AppFilterType.USER)
    private var currentUserId: Int= 0
    private var selectedTagIds: Set<String> = emptySet()

    // 缓存每个应用的标签，避免重复计算
    private var appTagsCache: Map<String, List<AppTag>> = emptyMap()

    /**
     * 设置应用列表（使用Map格式，包含用户分组信息）
     * @param appsMap UserInfoParcelable到应用列表的映射
     */
    fun setAppsMap(appsMap: Map<UserInfoParcelable, List<AppInfo>>) {
        this.appsMap = appsMap.map { it.key.id to it.value }.toMap()
        // 预计算所有应用的标签（轻量级）
        preloadAppTags()
        applyFilter()
    }

    /**
     * 获取当前用户或所有用户的应用列表
     */
    private fun getAppsForCurrentUser(): List<AppInfo> {
        return appsMap[currentUserId] ?: emptyList()
    }

    /**
     * 预计算所有应用的标签（只计算轻量级的内置标签）
     */
    private fun preloadAppTags() {
        val allApps = appsMap.flatMap { it.value }.distinctBy { it.packageName  }
        appTagsCache = allApps.associate { app ->
            app.packageName to AppTagHelper.getAppTags(app)
        }
    }

    fun setFilterType(filterType: Set<AppFilterType>) {
        currentFilterType = filterType
        applyFilter()
    }

    fun filterApps(query: String) {
        currentQuery = query
        applyFilter()
    }

    fun setUserFilter(userId: Int) {
        currentUserId = userId
        applyFilter()
    }

    /**
     * 设置选中的标签过滤
     */
    fun setSelectedTags(tagIds: Set<String>) {
        selectedTagIds = tagIds

        applyFilter()
    }

    /**
     * 获取当前选中的标签ID
     */
    fun getSelectedTags(): Set<String> = selectedTagIds

    /**
     * 清除标签过滤
     */
    fun clearTagFilter() {
        selectedTagIds = emptySet()
    }

    private fun applyFilter() {
        viewModelScope.launch {
            // 获取当前用户的应用列表
            var result = getAppsForCurrentUser()

            // 按标签过滤（使用缓存的标签）
            result = filterAppsByTagsWithCache(result, selectedTagIds)

            // 按搜索词和类型过滤
            filteredAppList.value =
                AppFilterHelper.filterApps(result, currentQuery, currentFilterType)
        }
    }

    /**
     * 使用缓存的标签进行过滤
     */
    private fun filterAppsByTagsWithCache(
        apps: List<AppInfo>,
        selectedTagIds: Set<String>
    ): List<AppInfo> {
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
            val cacheKey = appInfo.packageName
            val appTags = appTagsCache[cacheKey]?.map { it.id } ?: emptyList()

            // 检查内置标签是否都匹配
            val builtinMatch = builtinTagIds.all { it in appTags }

            // 如果没有分组标签过滤，直接返回内置标签匹配结果
            if (groupsToCheck.isEmpty()) {
                builtinMatch
            } else {
                // 需要检查分组标签 - 这个比较耗时
                builtinMatch && groupsToCheck.all { group ->
                    // 通过检查分组路径下是否存在该应用的目录来判断
                    val fs = tiiehenry.android.app.snapshot.SnapshotApp.getInstance().fileSystem
                    val appDir = "${group.path}/${appInfo.packageName}"
                    fs.fileType(appDir) == tiiehenry.android.snapshot.fs.IFileType.TYPE_DIR
                }
            }
        }
    }
}

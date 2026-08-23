package tiiehenry.android.app.snapshot.main.apps

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import tiiehenry.android.app.snapshot.app.AppFilterHelper
import tiiehenry.android.app.snapshot.app.AppFilterType
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.app.tag.AppTag
import tiiehenry.android.app.snapshot.app.tag.AppTagHelper
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.snapshot.app.UserInfoHide
import tiiehenry.android.snapshot.file.IFileSystem

class AppsViewModel : ViewModel() {

    val filteredAppList = MutableLiveData<List<AppInfo>>()
    private var appsMap: Map<Int, List<AppInfo>> = emptyMap()
    private var currentQuery: String = ""
    private var currentFilterType: Set<AppFilterType> = setOf(AppFilterType.SYSTEM, AppFilterType.USER)
    private var currentUserId: Int= 0
    private var selectedTagIds: Set<String> = emptySet()
    private var membershipFilter: MembershipFilter = MembershipFilter.ALL
    private val filterGeneration = AtomicInteger(0)

    enum class MembershipFilter {
        ALL,
        UNGROUPED_ONLY,
        GROUPED_ONLY,
    }

    // 缓存每个应用的标签，避免重复计算
    private var appTagsCache: Map<String, List<AppTag>> = emptyMap()

    // 依赖注入点，由 Fragment 在初始化后设置
    var fileSystem: IFileSystem? = null
    var groupsProvider: (() -> List<SnapGroup>)? = null

    /**
     * 设置应用列表（使用Map格式，包含用户分组信息）
     * @param appsMap UserInfoParcelable到应用列表的映射
     */
    fun setAppsMap(appsMap: Map<UserInfoHide, List<AppInfo>>) {
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

    fun setMembershipFilter(filter: MembershipFilter) {
        this.membershipFilter = filter
        applyFilter()
    }

    fun getMembershipFilter(): MembershipFilter = membershipFilter

    fun refreshMembershipFilter() {
        applyFilter()
    }

    /**
     * 还原搜索、系统/用户、用户 Tab、标签与分组筛选到默认状态。
     */
    fun resetFilters() {
        currentQuery = ""
        currentFilterType = setOf(AppFilterType.SYSTEM, AppFilterType.USER)
        currentUserId = 0
        selectedTagIds = emptySet()
        membershipFilter = MembershipFilter.ALL
    }

    private fun applyFilter() {
        val generation = filterGeneration.incrementAndGet()
        viewModelScope.launch {
            var result = getAppsForCurrentUser()
            result = filterAppsByTagsWithCache(result, selectedTagIds)
            result = filterByMembership(result)
            if (generation != filterGeneration.get()) return@launch
            filteredAppList.value =
                AppFilterHelper.filterApps(result, currentQuery, currentFilterType)
        }
    }

    private fun filterByMembership(apps: List<AppInfo>): List<AppInfo> {
        if (membershipFilter == MembershipFilter.ALL) {
            return apps
        }
        val groupedKeys = groupedAppKeys()
        return when (membershipFilter) {
            MembershipFilter.UNGROUPED_ONLY ->
                apps.filter { app -> "${app.packageName}:${app.userId}" !in groupedKeys }
            MembershipFilter.GROUPED_ONLY ->
                apps.filter { app -> "${app.packageName}:${app.userId}" in groupedKeys }
            MembershipFilter.ALL -> apps
        }
    }

    private fun groupedAppKeys(): Set<String> {
        val groups = groupsProvider?.invoke().orEmpty()
        val keys = HashSet<String>()
        for (group in groups) {
            if (!group.isExclusive) continue
            val members = synchronized(group.apps) { group.apps.toList() }
            for (archived in members) {
                val pkg = java.nio.file.Paths.get(archived.packageDir).fileName.toString()
                keys.add("$pkg:${group.userId}")
            }
        }
        return keys
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

        // 从groupsProvider获取已有的分组对象，避免重复创建
        val groupsToCheck: List<SnapGroup> = groupTagIds.mapNotNull { groupTagId ->
            val groupId = groupTagId.removePrefix("group_")
            groupsProvider?.invoke()?.find { group -> group.id == groupId }
        }

        val fs = fileSystem

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
                builtinMatch && fs != null && groupsToCheck.all { group ->
                    // 通过检查分组路径下是否存在该应用的目录来判断
                    val appDir = "${group.path}/${appInfo.packageName}"
                    fs.fileType(appDir) == tiiehenry.android.snapshot.fs.IFileType.TYPE_DIR
                }
            }
        }
    }
}

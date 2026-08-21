package tiiehenry.android.app.snapshot.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.config.ConfigFiles
import tiiehenry.android.app.snapshot.config.GlobalConfig
import tiiehenry.android.app.snapshot.group.AddAppItemResult
import tiiehenry.android.app.snapshot.group.AddAppsResult
import tiiehenry.android.app.snapshot.group.ArchiveRoot
import tiiehenry.android.app.snapshot.group.GroupMembershipMode
import tiiehenry.android.app.snapshot.group.GroupMembershipResolver
import tiiehenry.android.app.snapshot.group.GroupSetColors
import tiiehenry.android.app.snapshot.group.MoveAppResult
import tiiehenry.android.app.snapshot.group.PackageOpGuard
import tiiehenry.android.app.snapshot.group.SetMembershipModeResult
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.group.SnapGroupSet
import tiiehenry.android.app.snapshot.main.launch.ArchiveListItem
import tiiehenry.android.app.snapshot.main.launch.batch.RestoreRecordStore
import tiiehenry.android.app.snapshot.utils.AppIconUtils
import tiiehenry.android.snapshot.app.IAppManager
import tiiehenry.android.snapshot.app.UserInfoHide
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.fs.IFileType
import java.nio.file.Paths
import java.util.UUID
import kotlin.io.path.absolutePathString

/**
 * 应用数据仓库 - 单例
 * 管理分组/分组集登记、应用列表；存档列表形状只经 [archiveList] 投影排放。
 * 独占归属与 packageDir 占用以本类为 SSOT。
 */
class AppDataRepository private constructor() {

    companion object {
        const val TAG = "AppDataRepository"

        @Volatile
        private var instance: AppDataRepository? = null

        fun getInstance(): AppDataRepository {
            return instance ?: synchronized(this) {
                instance ?: AppDataRepository().also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loadGroupsMutex = Mutex()

    /**
     * [loadGroupsMutex] 内分组/集列表的唯一读源。LiveData 只 [MutableLiveData.postValue]，禁止锁内读 `*.value`。
     */
    private var loadedGroups: List<SnapGroup> = emptyList()
    private var loadedSets: List<SnapGroupSet> = emptyList()

    /** 进程级占用 SSOT；ViewModel 批门闩仅为 facade。 */
    val packageOpGuard = PackageOpGuard()

    val groupList = MutableLiveData<List<SnapGroup>>()
    val groupSetList = MutableLiveData<List<SnapGroupSet>>(emptyList())
    /** 存档 Tab SSOT；Adapter 只观察此列表。 */
    val archiveList = MutableLiveData<List<ArchiveListItem>>(emptyList())
    val appsList = MutableLiveData<Map<UserInfoHide, List<AppInfo>>>(emptyMap())

    /**
     * 已安装应用 catalog 的加载态 SSOT。
     */
    val isAppsLoading = MutableLiveData(false)

    fun loadData(context: Context, fileSystem: IFileSystem, appManager: IAppManager) {
        scope.launch {
            isAppsLoading.postValue(true)
            loadGroups(context, fileSystem, appManager)
            loadApps(fileSystem, appManager)
        }
    }

    fun scheduleLoadGroups(context: Context, fileSystem: IFileSystem, appManager: IAppManager) {
        scope.launch {
            loadGroups(context, fileSystem, appManager)
        }
    }

    suspend fun loadGroups(context: Context, fileSystem: IFileSystem, appManager: IAppManager) {
        loadGroupsMutex.withLock {
            reloadGroupsLocked(context, fileSystem, appManager)
        }
    }

    private suspend fun reloadGroupsLocked(
        context: Context,
        fileSystem: IFileSystem,
        appManager: IAppManager,
    ) {
        Log.i(TAG, "loadGroups")
        GlobalConfig.ensureArchiveRootsMigrated()

        val groupIds = GlobalConfig.groups
        val existingGroups = loadedGroups.associateBy { it.id }
        val groups = groupIds.map { groupId ->
            Log.i(TAG, "loadGroup: $groupId")
            (existingGroups[groupId] ?: SnapGroup(groupId)).apply {
                loadApps(context, fileSystem, appManager, true)
            }
        }

        val setIds = GlobalConfig.groupSetIds
        val existingSets = loadedSets.associateBy { it.id }
        val sets = setIds.map { setId ->
            existingSets[setId] ?: SnapGroupSet(setId)
        }

        val membersBySetId = deriveLiveMembers(sets, groups)
        val memberGroupIds = membersBySetId.values.flatten().map { it.id }.toSet()

        val reconciled = ArchiveListProjector.reconcileRoots(
            roots = GlobalConfig.archiveRoots,
            allGroupIds = groups.map { it.id }.toSet(),
            memberGroupIds = memberGroupIds,
            setIds = sets.map { it.id }.toSet(),
        )
        if (reconciled != GlobalConfig.archiveRoots) {
            GlobalConfig.archiveRoots = reconciled
        }

        loadedGroups = groups
        loadedSets = sets
        groupList.postValue(groups)
        groupSetList.postValue(sets)
        reprojectArchiveListLocked()

        val corrupt = GroupMembershipResolver.corruptKeys(groups)
        if (corrupt.isNotEmpty()) {
            Log.w(TAG, "exclusive multi-owner corruption: $corrupt")
        }
    }

    /**
     * 只读 [loadedGroups]/[loadedSets] 投影存档列表形状。**只** [archiveList.postValue]。
     * 必须在 [loadGroupsMutex] 内调用。
     */
    private fun reprojectArchiveListLocked() {
        val groups = loadedGroups
        val sets = loadedSets
        val groupsById = groups.associateBy { it.id }
        val setsById = sets.associateBy { it.id }
        val membersBySetId = deriveLiveMembers(sets, groups)
        val draft = ArchiveListProjector.project(
            ArchiveListProjector.Input(
                roots = GlobalConfig.archiveRoots,
                setsById = setsById.mapValues { (_, s) ->
                    ArchiveListProjector.SetSnap(s.id, s.isCollapsed, s.groupOrder)
                },
                groupsById = groupsById.mapValues { (_, g) ->
                    ArchiveListProjector.GroupSnap(g.id, g.path)
                },
                membersBySetId = membersBySetId.mapValues { (_, list) ->
                    list.map { ArchiveListProjector.GroupSnap(it.id, it.path) }
                },
            )
        )
        archiveList.postValue(materializeArchiveList(draft, setsById, groupsById, membersBySetId))
    }

    private fun deriveLiveMembers(
        sets: List<SnapGroupSet>,
        groups: List<SnapGroup>,
    ): Map<String, List<SnapGroup>> {
        val setSnaps = sets.map {
            ArchiveListProjector.SetSnap(it.id, it.isCollapsed, it.groupOrder)
        }
        val groupSnaps = groups.map {
            ArchiveListProjector.GroupSnap(it.id, it.path)
        }
        val setPaths = sets.associate { it.id to it.path }
        val derived = ArchiveListProjector.deriveMembers(
            sets = setSnaps,
            groups = groupSnaps,
            setPaths = setPaths,
            roots = GlobalConfig.archiveRoots,
        )
        val groupsById = groups.associateBy { it.id }
        return derived.mapValues { (_, snaps) ->
            snaps.mapNotNull { groupsById[it.id] }
        }
    }

    private fun materializeArchiveList(
        draft: List<ArchiveListProjector.DraftItem>,
        setsById: Map<String, SnapGroupSet>,
        groupsById: Map<String, SnapGroup>,
        membersBySetId: Map<String, List<SnapGroup>>,
    ): List<ArchiveListItem> {
        return draft.mapNotNull { item ->
            when (item) {
                is ArchiveListProjector.DraftItem.SetHeader -> {
                    val set = setsById[item.setId] ?: return@mapNotNull null
                    ArchiveListItem.SetHeader(
                        set = set,
                        groupCount = item.groupCount,
                        expanded = item.expanded,
                        name = set.name,
                        accentColor = set.accentColor,
                    )
                }
                is ArchiveListProjector.DraftItem.GroupCard -> {
                    val group = groupsById[item.groupId] ?: return@mapNotNull null
                    val accent = item.setId?.let { setsById[it]?.accentColor }
                    ArchiveListItem.GroupCard(
                        group = group,
                        setId = item.setId,
                        accentColor = accent,
                        collapsed = group.isCollapsed,
                    )
                }
                is ArchiveListProjector.DraftItem.EmptySetHint -> {
                    val set = setsById[item.setId] ?: return@mapNotNull null
                    ArchiveListItem.EmptySetHint(set, set.accentColor)
                }
            }
        }
    }

    suspend fun loadApps(fileSystem: IFileSystem, appManager: IAppManager) {
        Log.i(TAG, "loadApps")
        isAppsLoading.postValue(true)
        try {
            val appsMap = withContext(Dispatchers.IO) {
                val appsMap = mutableMapOf<UserInfoHide, List<AppInfo>>()
                val userInfos = appManager.users ?: listOf()

                Log.i(TAG, "loadApps: userInfos $userInfos")
                for (userInfo in userInfos) {
                    val userId = userInfo.id
                    try {
                        val packageNames = appManager.getInstalledPackages(0, userId) ?: emptyList()
                        val apps = packageNames.mapNotNull { packageName ->
                            try {
                                val packageInfo = appManager.getPackageInfo(packageName, 0, userId)
                                AppInfo(
                                    fs = fileSystem,
                                    appManager = appManager,
                                    packageName = packageName,
                                    userId = userId,
                                    versionName = packageInfo?.versionName,
                                    versionCode = packageInfo?.longVersionCode ?: 0
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                                null
                            }
                        }
                        appsMap[userInfo] = apps
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load apps for user $userId", e)
                    }
                }
                appsMap
            }
            appsList.postValue(appsMap)
        } catch (e: Exception) {
            e.printStackTrace()
            appsList.postValue(emptyMap())
        } finally {
            isAppsLoading.postValue(false)
        }
    }

    fun addGroup(
        context: Context,
        fileSystem: IFileSystem,
        appManager: IAppManager,
        name: String,
        path: String,
        userId: Int = 0,
        onComplete: ((PathRegistrationResult) -> Unit)? = null,
    ) {
        scope.launch {
            val result = try {
                loadGroupsMutex.withLock {
                    val normalizedPath = GroupSetMembership.normalizePath(path)
                    when {
                        isPathOccupiedBySet(normalizedPath) -> {
                            Log.w(TAG, "addGroup rejected: path used as set: $normalizedPath")
                            PathRegistrationResult.OccupiedBySet
                        }
                        isPathOccupiedByGroup(normalizedPath) -> {
                            Log.w(TAG, "addGroup rejected: path used as group: $normalizedPath")
                            PathRegistrationResult.OccupiedByGroup
                        }
                        else -> {
                            val groupId = UUID.randomUUID().toString().substring(0, 7)
                            if (!fileSystem.exists(normalizedPath)) {
                                fileSystem.mkdirs(normalizedPath)
                            }
                            val group = SnapGroup(groupId)
                            group.path = normalizedPath
                            group.name = name
                            group.config.groupConfigData.userId = userId
                            group.config.save()

                            GlobalConfig.groups = GlobalConfig.groups.toMutableList().apply { add(groupId) }

                            val parent = GroupSetMembership.parentPath(normalizedPath)
                            val belongingSet = loadedSets
                                .firstOrNull { GroupSetMembership.normalizePath(it.path) == parent }
                            if (belongingSet == null) {
                                GlobalConfig.archiveRoots = GlobalConfig.archiveRoots + ArchiveRoot.Group(groupId)
                            } else {
                                appendBasenameToSetOrder(belongingSet, GroupSetMembership.basename(normalizedPath))
                            }
                            reloadGroupsLocked(context, fileSystem, appManager)
                            PathRegistrationResult.Ok()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "addGroup failed", e)
                PathRegistrationResult.Error(e.message ?: "addGroup failed")
            }
            withContext(Dispatchers.Main) { onComplete?.invoke(result) }
        }
    }

    fun addGroupSet(
        context: Context,
        fileSystem: IFileSystem,
        appManager: IAppManager,
        name: String,
        path: String,
        onComplete: ((PathRegistrationResult) -> Unit)? = null,
    ) {
        scope.launch {
            val result = try {
                loadGroupsMutex.withLock {
                    val normalizedPath = GroupSetMembership.normalizePath(path)
                    when {
                        isPathOccupiedBySet(normalizedPath) -> {
                            Log.w(TAG, "addGroupSet rejected: set path already exists")
                            PathRegistrationResult.OccupiedBySet
                        }
                        isPathOccupiedByGroup(normalizedPath) -> {
                            Log.w(TAG, "addGroupSet rejected: path equals an existing group; upgrade empty group first")
                            PathRegistrationResult.OccupiedByGroup
                        }
                        else -> {
                            if (!fileSystem.exists(normalizedPath)) {
                                fileSystem.mkdirs(normalizedPath)
                            }
                            val setId = UUID.randomUUID().toString().substring(0, 7)
                            val set = SnapGroupSet(setId)
                            set.path = normalizedPath
                            set.name = name
                            set.isCollapsed = true
                            set.accentColor = GroupSetColors.defaultFor(setId)
                            set.save()

                            GlobalConfig.archiveRoots = GlobalConfig.archiveRoots + ArchiveRoot.Set(setId)
                            val discovered = discoverGroupsLocked(fileSystem, set)
                            reloadGroupsLocked(context, fileSystem, appManager)
                            PathRegistrationResult.Ok(discovered)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "addGroupSet failed", e)
                PathRegistrationResult.Error(e.message ?: "addGroupSet failed")
            }
            withContext(Dispatchers.Main) { onComplete?.invoke(result) }
        }
    }

    /**
     * 扫描集目录直接子目录并登记为分组。返回新登记数量。
     */
    private fun discoverGroupsLocked(fileSystem: IFileSystem, set: SnapGroupSet): Int {
        val setPath = GroupSetMembership.normalizePath(set.path)
        val names = try {
            fileSystem.listDir(setPath) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "discoverGroups listDir failed", e)
            emptyList()
        }

        val pathToGroup = mutableMapOf<String, SnapGroup>()
        for (id in GlobalConfig.groups) {
            val g = loadedGroups.find { it.id == id } ?: SnapGroup(id)
            pathToGroup[GroupSetMembership.normalizePath(g.path)] = g
        }

        var newCount = 0
        val foundBasenames = mutableListOf<String>()
        val registeredIds = GlobalConfig.groups.toMutableList()

        for (name in names) {
            if (name.startsWith(".")) continue
            val childPath = Paths.get(setPath, name).absolutePathString()
            val type = try {
                fileSystem.fileType(childPath)
            } catch (e: Exception) {
                continue
            }
            if (type != IFileType.TYPE_DIR) continue

            val hasGroupJson = fileSystem.exists(
                Paths.get(childPath, ConfigFiles.GROUP_CONFIG_FILE).absolutePathString()
            )
            if (GroupSetMembership.looksLikePackageName(name) && !hasGroupJson) {
                Log.i(TAG, "discoverGroups skip package-like dir without group.json: $name")
                continue
            }

            foundBasenames += name
            val normalizedChild = GroupSetMembership.normalizePath(childPath)
            if (pathToGroup.containsKey(normalizedChild)) {
                continue
            }
            val groupId = UUID.randomUUID().toString().substring(0, 7)
            val group = SnapGroup(groupId)
            group.path = childPath
            if (hasGroupJson) {
                group.config.load()
            } else {
                group.name = name
                group.config.save()
            }
            registeredIds += groupId
            pathToGroup[normalizedChild] = group
            newCount++
        }

        // Remove groups under this set whose directories disappeared
        val toRemove = registeredIds.filter { id ->
            val g = pathToGroup.values.find { it.id == id } ?: SnapGroup(id)
            val p = GroupSetMembership.normalizePath(g.path)
            GroupSetMembership.isMemberOf(p, setPath) &&
                GroupSetMembership.basename(p) !in foundBasenames
        }
        for (id in toRemove) {
            registeredIds.remove(id)
            SnapGroup(id).config.mmkv.clearAll()
        }

        GlobalConfig.groups = registeredIds.distinct()

        val order = set.groupOrder.toMutableList()
        for (base in foundBasenames) {
            if (base !in order) order += base
        }
        order.removeAll { it !in foundBasenames }
        set.groupOrder = order
        set.save()

        val memberIds = registeredIds.filter { id ->
            val g = pathToGroup.values.find { it.id == id } ?: SnapGroup(id)
            GroupSetMembership.isMemberOf(
                GroupSetMembership.normalizePath(g.path),
                setPath,
            )
        }.toSet()
        GlobalConfig.archiveRoots = GlobalConfig.archiveRoots.filterNot {
            it is ArchiveRoot.Group && it.groupId in memberIds
        }

        return newCount
    }

    fun refreshGroupSet(
        context: Context,
        fileSystem: IFileSystem,
        appManager: IAppManager,
        setId: String,
        onComplete: ((Int) -> Unit)? = null,
    ) {
        scope.launch {
            var count = 0
            try {
                loadGroupsMutex.withLock {
                    val set = (loadedSets.find { it.id == setId }
                        ?: SnapGroupSet(setId))
                    count = discoverGroupsLocked(fileSystem, set)
                    reloadGroupsLocked(context, fileSystem, appManager)
                }
                withContext(Dispatchers.Main) { onComplete?.invoke(count) }
            } catch (e: Exception) {
                Log.e(TAG, "refreshGroupSet failed", e)
                withContext(Dispatchers.Main) { onComplete?.invoke(count) }
            }
        }
    }

    enum class DeleteGroupSetMode {
        /** 仅移除集登记；子分组变为独立 */
        SET_ONLY,
        /** 移除集与子分组登记，不删文件 */
        SET_AND_GROUPS,
        /** 删除集目录 */
        DELETE_FILES,
    }

    fun deleteGroupSet(
        context: Context,
        fileSystem: IFileSystem,
        appManager: IAppManager,
        setId: String,
        mode: DeleteGroupSetMode = DeleteGroupSetMode.SET_ONLY,
        onComplete: (() -> Unit)? = null,
    ) {
        scope.launch {
            try {
                loadGroupsMutex.withLock {
                    val set = loadedSets.find { it.id == setId }
                        ?: SnapGroupSet(setId)
                    val setPath = GroupSetMembership.normalizePath(set.path)
                    val memberIds = GlobalConfig.groups.filter { id ->
                        GroupSetMembership.isMemberOf(SnapGroup(id).path, setPath)
                    }
                    val roots = GlobalConfig.archiveRoots.toMutableList()
                    val setIndex = roots.indexOfFirst { it is ArchiveRoot.Set && it.setId == setId }
                    roots.removeAll { it is ArchiveRoot.Set && it.setId == setId }

                    when (mode) {
                        DeleteGroupSetMode.SET_ONLY -> {
                            val insertAt = if (setIndex >= 0) setIndex else roots.size
                            val independents = memberIds.map { ArchiveRoot.Group(it) }
                            roots.addAll(insertAt, independents)
                        }
                        DeleteGroupSetMode.SET_AND_GROUPS,
                        DeleteGroupSetMode.DELETE_FILES -> {
                            GlobalConfig.groups = GlobalConfig.groups.filterNot { it in memberIds }
                            memberIds.forEach { SnapGroup(it).config.mmkv.clearAll() }
                            if (mode == DeleteGroupSetMode.DELETE_FILES) {
                                fileSystem.delete(set.path)
                            }
                        }
                    }
                    GlobalConfig.archiveRoots = roots
                    set.config.clearLocal()
                    reloadGroupsLocked(context, fileSystem, appManager)
                }
                withContext(Dispatchers.Main) { onComplete?.invoke() }
            } catch (e: Exception) {
                Log.e(TAG, "deleteGroupSet failed", e)
                withContext(Dispatchers.Main) { onComplete?.invoke() }
            }
        }
    }

    /** 形状-only：写集 [SnapGroupSet.isCollapsed] 后内存再投影，不扫盘。未知 [setId] no-op。 */
    fun setGroupSetCollapsed(setId: String, collapsed: Boolean) {
        scope.launch {
            try {
                loadGroupsMutex.withLock {
                    val set = loadedSets.find { it.id == setId } ?: return@withLock
                    set.isCollapsed = collapsed
                    reprojectArchiveListLocked()
                }
            } catch (e: Exception) {
                Log.e(TAG, "setGroupSetCollapsed failed", e)
            }
        }
    }

    /** 一键折叠：所有分组集 Header + 所有分组卡片 body。形状-only，不扫盘。 */
    fun collapseAllArchive() {
        scope.launch {
            try {
                loadGroupsMutex.withLock {
                    for (set in loadedSets) {
                        set.isCollapsed = true
                    }
                    for (group in loadedGroups) {
                        group.isCollapsed = true
                    }
                    reprojectArchiveListLocked()
                }
            } catch (e: Exception) {
                Log.e(TAG, "collapseAllArchive failed", e)
            }
        }
    }

    fun saveArchiveRootsOrder(
        context: Context,
        fileSystem: IFileSystem,
        appManager: IAppManager,
        roots: List<ArchiveRoot>,
        onComplete: (() -> Unit)? = null,
    ) {
        scope.launch {
            try {
                loadGroupsMutex.withLock {
                    // ID 集合不变：只重排
                    val oldIds = GlobalConfig.groups.toSet()
                    GlobalConfig.archiveRoots = roots
                    check(GlobalConfig.groups.toSet() == oldIds) {
                        "saveArchiveRootsOrder must not change groups set"
                    }
                    reloadGroupsLocked(context, fileSystem, appManager)
                }
                withContext(Dispatchers.Main) { onComplete?.invoke() }
            } catch (e: Exception) {
                Log.e(TAG, "saveArchiveRootsOrder failed", e)
                withContext(Dispatchers.Main) { onComplete?.invoke() }
            }
        }
    }

    fun saveGroupSetOrder(
        context: Context,
        fileSystem: IFileSystem,
        appManager: IAppManager,
        setId: String,
        basenames: List<String>,
        onComplete: (() -> Unit)? = null,
    ) {
        scope.launch {
            try {
                loadGroupsMutex.withLock {
                    val set = loadedSets.find { it.id == setId }
                        ?: SnapGroupSet(setId)
                    set.groupOrder = basenames
                    set.save()
                    reloadGroupsLocked(context, fileSystem, appManager)
                }
                withContext(Dispatchers.Main) { onComplete?.invoke() }
            } catch (e: Exception) {
                Log.e(TAG, "saveGroupSetOrder failed", e)
                withContext(Dispatchers.Main) { onComplete?.invoke() }
            }
        }
    }

    fun updateGroupPath(
        context: Context,
        fileSystem: IFileSystem,
        appManager: IAppManager,
        groupId: String,
        newPath: String,
        newName: String? = null,
        userId: Int? = null,
        onComplete: ((PathRegistrationResult) -> Unit)? = null,
    ) {
        scope.launch {
            val result = try {
                loadGroupsMutex.withLock {
                    val group = loadedGroups.find { it.id == groupId }
                        ?: SnapGroup(groupId)
                    val oldPath = GroupSetMembership.normalizePath(group.path)
                    val oldBase = GroupSetMembership.basename(oldPath)
                    val normalized = GroupSetMembership.normalizePath(newPath)
                    val newBase = GroupSetMembership.basename(normalized)

                    if (normalized != oldPath) {
                        when {
                            isPathOccupiedBySet(normalized) -> {
                                Log.w(TAG, "updateGroupPath rejected: path used as set: $normalized")
                                return@withLock PathRegistrationResult.OccupiedBySet
                            }
                            isPathOccupiedByGroup(normalized, excludeGroupId = groupId) -> {
                                Log.w(TAG, "updateGroupPath rejected: path used as group: $normalized")
                                return@withLock PathRegistrationResult.OccupiedByGroup
                            }
                        }
                    }

                    group.path = normalized
                    if (newName != null) group.name = newName
                    if (userId != null) group.userId = userId
                    group.config.save()

                    // Same set, basename changed → rewrite groupOrder
                    if (oldBase != newBase) {
                        for (set in loadedSets) {
                            val setPath = GroupSetMembership.normalizePath(set.path)
                            val wasMember = GroupSetMembership.isMemberOf(oldPath, setPath)
                            val isMember = GroupSetMembership.isMemberOf(normalized, setPath)
                            if (wasMember && isMember) {
                                val order = set.groupOrder.toMutableList()
                                val idx = order.indexOf(oldBase)
                                if (idx >= 0) {
                                    order[idx] = newBase
                                } else if (newBase !in order) {
                                    order += newBase
                                }
                                set.groupOrder = order
                                set.save()
                            }
                        }
                    }
                    reloadGroupsLocked(context, fileSystem, appManager)
                    PathRegistrationResult.Ok()
                }
            } catch (e: Exception) {
                Log.e(TAG, "updateGroupPath failed", e)
                PathRegistrationResult.Error(e.message ?: "updateGroupPath failed")
            }
            withContext(Dispatchers.Main) { onComplete?.invoke(result) }
        }
    }

    fun updateGroupSetPath(
        context: Context,
        fileSystem: IFileSystem,
        appManager: IAppManager,
        setId: String,
        newPath: String,
        newName: String? = null,
        accentColor: Int? = null,
        onComplete: ((PathRegistrationResult) -> Unit)? = null,
    ) {
        scope.launch {
            val result = try {
                loadGroupsMutex.withLock {
                    val set = loadedSets.find { it.id == setId }
                        ?: SnapGroupSet(setId)
                    val oldPath = GroupSetMembership.normalizePath(set.path)
                    val normalized = GroupSetMembership.normalizePath(newPath)
                    if (normalized != oldPath) {
                        when {
                            isPathOccupiedBySet(normalized, excludeSetId = setId) -> {
                                Log.w(TAG, "updateGroupSetPath rejected: path used as set: $normalized")
                                return@withLock PathRegistrationResult.OccupiedBySet
                            }
                            isPathOccupiedByGroup(normalized) -> {
                                Log.w(TAG, "updateGroupSetPath rejected: path used as group: $normalized")
                                return@withLock PathRegistrationResult.OccupiedByGroup
                            }
                        }
                    }
                    set.path = normalized
                    if (newName != null) set.name = newName
                    if (accentColor != null) set.accentColor = accentColor
                    set.save()
                    discoverGroupsLocked(fileSystem, set)
                    reloadGroupsLocked(context, fileSystem, appManager)
                    PathRegistrationResult.Ok()
                }
            } catch (e: Exception) {
                Log.e(TAG, "updateGroupSetPath failed", e)
                PathRegistrationResult.Error(e.message ?: "updateGroupSetPath failed")
            }
            withContext(Dispatchers.Main) { onComplete?.invoke(result) }
        }
    }

    /**
     * 将空 SnapGroup（path 将成为集目录）升级为分组集：先取消分组登记再 addGroupSet。
     */
    fun upgradeEmptyGroupToSet(
        context: Context,
        fileSystem: IFileSystem,
        appManager: IAppManager,
        groupId: String,
        setName: String,
        onComplete: ((Int) -> Unit)? = null,
    ) {
        scope.launch {
            var discovered = 0
            try {
                loadGroupsMutex.withLock {
                    val group = loadedGroups.find { it.id == groupId }
                        ?: SnapGroup(groupId)
                    val path = GroupSetMembership.normalizePath(group.path)
                    GlobalConfig.groups = GlobalConfig.groups.filterNot { it == groupId }
                    GlobalConfig.archiveRoots = GlobalConfig.archiveRoots.filterNot {
                        it is ArchiveRoot.Group && it.groupId == groupId
                    }
                    group.config.mmkv.clearAll()

                    val setId = UUID.randomUUID().toString().substring(0, 7)
                    val set = SnapGroupSet(setId)
                    set.path = path
                    set.name = setName
                    set.isCollapsed = true
                    set.save()
                    GlobalConfig.archiveRoots = GlobalConfig.archiveRoots + ArchiveRoot.Set(setId)
                    discovered = discoverGroupsLocked(fileSystem, set)
                    reloadGroupsLocked(context, fileSystem, appManager)
                }
                withContext(Dispatchers.Main) { onComplete?.invoke(discovered) }
            } catch (e: Exception) {
                Log.e(TAG, "upgradeEmptyGroupToSet failed", e)
                withContext(Dispatchers.Main) { onComplete?.invoke(discovered) }
            }
        }
    }

    fun addAppsToGroup(
        context: Context,
        fileSystem: IFileSystem,
        appManager: IAppManager,
        groupId: String,
        currentGroups: List<SnapGroup>,
        appInfos: List<AppInfo>,
        onComplete: ((AddAppsResult) -> Unit)? = null
    ) {
        scope.launch {
            val resultItems = linkedMapOf<String, AddAppItemResult>()
            try {
                loadGroupsMutex.withLock {
                    val groups = loadedGroups.ifEmpty { currentGroups }
                    val group = groups.find { it.id == groupId }
                    if (group == null) {
                        for (info in appInfos) {
                            resultItems[info.packageName] =
                                AddAppItemResult.Error("group not found: $groupId")
                        }
                        return@withLock
                    }
                    val targetExclusive = group.isExclusive
                    var wrote = false
                    for (appInfo in appInfos) {
                        val packageName = appInfo.packageName
                        val packageDir = Paths.get(group.path, packageName).absolutePathString()
                        if (packageOpGuard.isBusy(packageDir) || packageOpGuard.isGlobalBatchRunning()) {
                            resultItems[packageName] = AddAppItemResult.Busy
                            continue
                        }
                        if (GroupMembershipResolver.containsPackage(group, packageName)) {
                            resultItems[packageName] = AddAppItemResult.AlreadyHere
                            continue
                        }
                        if (targetExclusive) {
                            val owners = GroupMembershipResolver.findExclusiveOwners(
                                groups, packageName, group.userId
                            )
                            when {
                                owners.size >= 2 -> {
                                    resultItems[packageName] = AddAppItemResult.CorruptMultiOwner
                                    continue
                                }
                                owners.size == 1 && owners[0].id != group.id -> {
                                    resultItems[packageName] =
                                        AddAppItemResult.Conflict(owners[0].id)
                                    continue
                                }
                            }
                        }
                        try {
                            if (!fileSystem.exists(packageDir)) {
                                fileSystem.mkdirs(packageDir)
                            }
                            val iconFile =
                                Paths.get(group.path, "$packageName.png").absolutePathString()
                            AppIconUtils.loadAndSaveAppIcon(
                                context,
                                fileSystem,
                                appManager,
                                packageName,
                                0,
                                iconFile
                            )
                            resultItems[packageName] = AddAppItemResult.Added
                            wrote = true
                        } catch (e: Exception) {
                            Log.e(TAG, "addAppsToGroup failed for $packageName", e)
                            resultItems[packageName] =
                                AddAppItemResult.Error(e.message ?: "add failed")
                        }
                    }
                    if (wrote) {
                        reloadGroupsLocked(context, fileSystem, appManager)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "addAppsToGroup Error: ${e.message}", e)
                for (info in appInfos) {
                    resultItems.putIfAbsent(
                        info.packageName,
                        AddAppItemResult.Error(e.message ?: "add failed")
                    )
                }
            }
            val result = AddAppsResult(resultItems)
            withContext(Dispatchers.Main) {
                onComplete?.invoke(result)
            }
        }
    }

    fun setMembershipMode(
        context: Context,
        fileSystem: IFileSystem,
        appManager: IAppManager,
        groupId: String,
        mode: GroupMembershipMode,
        onComplete: ((SetMembershipModeResult) -> Unit)? = null,
    ) {
        scope.launch {
            val outcome = try {
                loadGroupsMutex.withLock {
                    val groups = loadedGroups
                    val group = groups.find { it.id == groupId }
                        ?: return@withLock SetMembershipModeResult.Error("group not found")
                    if (group.membershipMode == mode) {
                        return@withLock SetMembershipModeResult.Ok
                    }
                    if (mode == GroupMembershipMode.EXCLUSIVE) {
                        val conflicts = mutableListOf<String>()
                        val members = synchronized(group.apps) { group.apps.toList() }
                        for (app in members) {
                            val pkg = GroupMembershipResolver.packageNameOf(app)
                            val others = GroupMembershipResolver.findExclusiveOwners(
                                groups, pkg, group.userId
                            ).filter { it.id != group.id }
                            if (others.isNotEmpty()) {
                                conflicts += pkg
                            }
                        }
                        if (conflicts.isNotEmpty()) {
                            return@withLock SetMembershipModeResult.Conflict(conflicts.distinct())
                        }
                    }
                    group.config.groupConfigData.membershipMode = mode.toStorage()
                    group.config.save()
                    reloadGroupsLocked(context, fileSystem, appManager)
                    SetMembershipModeResult.Ok
                }
            } catch (e: Exception) {
                Log.e(TAG, "setMembershipMode failed", e)
                SetMembershipModeResult.Error(e.message ?: "setMembershipMode failed")
            }
            withContext(Dispatchers.Main) {
                onComplete?.invoke(outcome)
            }
        }
    }

    fun moveAppBetweenGroups(
        context: Context,
        fileSystem: IFileSystem,
        appManager: IAppManager,
        fromGroupId: String,
        toGroupId: String,
        packageName: String,
        onComplete: ((MoveAppResult) -> Unit)? = null,
    ) {
        scope.launch {
            val outcome = moveAppBetweenGroupsLocked(
                context, fileSystem, appManager, fromGroupId, toGroupId, packageName
            )
            withContext(Dispatchers.Main) {
                onComplete?.invoke(outcome)
            }
        }
    }

    private suspend fun moveAppBetweenGroupsLocked(
        context: Context,
        fileSystem: IFileSystem,
        appManager: IAppManager,
        fromGroupId: String,
        toGroupId: String,
        packageName: String,
    ): MoveAppResult {
        var sourceDir: String? = null
        var targetDir: String? = null
        var beganSource = false
        var beganTarget = false
        try {
            val precheck = loadGroupsMutex.withLock {
                val groups = loadedGroups
                val source = groups.find { it.id == fromGroupId }
                    ?: return@withLock MoveAppResult.Error("source group not found")
                val target = groups.find { it.id == toGroupId }
                    ?: return@withLock MoveAppResult.Error("target group not found")
                if (!target.isExclusive) {
                    return@withLock MoveAppResult.Error("target must be exclusive")
                }
                if (!source.isExclusive) {
                    return@withLock MoveAppResult.Error("source must be exclusive")
                }
                val owners = GroupMembershipResolver.findExclusiveOwners(
                    groups, packageName, target.userId
                )
                if (owners.size >= 2) {
                    return@withLock MoveAppResult.CorruptMultiOwner
                }
                if (owners.size == 1 && owners[0].id != source.id) {
                    return@withLock MoveAppResult.Error("source is not exclusive owner")
                }
                if (source.config.isLocked(packageName)) {
                    return@withLock MoveAppResult.Locked
                }
                if (source.userId != target.userId) {
                    return@withLock MoveAppResult.Error("userId mismatch")
                }
                val src = Paths.get(source.path, packageName).absolutePathString()
                val dst = Paths.get(target.path, packageName).absolutePathString()
                Triple(source, target, src to dst)
            }
            if (precheck is MoveAppResult) return precheck
            val (source, target, dirs) = precheck as Triple<SnapGroup, SnapGroup, Pair<String, String>>
            sourceDir = dirs.first
            targetDir = dirs.second

            if (!packageOpGuard.tryBeginPackageOp(sourceDir!!)) {
                return MoveAppResult.Busy
            }
            beganSource = true
            if (sourceDir != targetDir) {
                if (!packageOpGuard.tryBeginPackageOp(targetDir!!)) {
                    return MoveAppResult.Busy
                }
                beganTarget = true
            }

            val srcExists = fileSystem.exists(sourceDir!!)
            val dstExists = fileSystem.exists(targetDir!!)

            if (!srcExists && dstExists && isCompletePackageDir(fileSystem, targetDir!!)) {
                return loadGroupsMutex.withLock {
                    finalizeMoveMetadata(source, target, packageName)
                    reloadGroupsLocked(context, fileSystem, appManager)
                    MoveAppResult.AlreadyAtTarget
                }
            }
            if (!srcExists && !dstExists) {
                return MoveAppResult.Error("source and target missing")
            }
            if (!srcExists) {
                return MoveAppResult.Error("source packageDir missing")
            }

            if (dstExists) {
                when {
                    isCompletelyEmptyDir(fileSystem, targetDir!!) -> {
                        fileSystem.delete(targetDir!!)
                    }
                    isIncompleteRelativeTo(fileSystem, sourceDir!!, targetDir!!) -> {
                        fileSystem.delete(targetDir!!)
                    }
                    else -> return MoveAppResult.TargetNonEmpty
                }
            }

            val moved = fileSystem.move(sourceDir!!, targetDir!!)
            if (!moved) {
                val copied = fileSystem.copyRecursively(sourceDir!!, targetDir!!, false)
                if (!copied || !isCompleteRelativeTo(fileSystem, sourceDir!!, targetDir!!)) {
                    if (fileSystem.exists(targetDir!!)) {
                        runCatching { fileSystem.delete(targetDir!!) }
                    }
                    return MoveAppResult.Error("move/copy failed")
                }
                if (!fileSystem.delete(sourceDir!!)) {
                    Log.w(TAG, "copied but failed to delete source: $sourceDir")
                    return MoveAppResult.Error("copied but source delete failed")
                }
            }

            moveGroupIcon(fileSystem, source.path, target.path, packageName)

            return loadGroupsMutex.withLock {
                finalizeMoveMetadata(source, target, packageName)
                reloadGroupsLocked(context, fileSystem, appManager)
                MoveAppResult.Moved
            }
        } catch (e: Exception) {
            Log.e(TAG, "moveAppBetweenGroups failed", e)
            return MoveAppResult.Error(e.message ?: "move failed")
        } finally {
            if (beganTarget && targetDir != null) packageOpGuard.endPackageOp(targetDir!!)
            if (beganSource && sourceDir != null) packageOpGuard.endPackageOp(sourceDir!!)
        }
    }

    private fun finalizeMoveMetadata(
        source: SnapGroup,
        target: SnapGroup,
        packageName: String,
    ) {
        val sourceSort = source.config.sortConfig.sortOrder
        if (sourceSort.remove(packageName)) {
            source.config.save()
        }
        source.config.removeFromLockedList(packageName)

        val targetSort = target.config.sortConfig.sortOrder
        if (packageName !in targetSort) {
            targetSort.add(packageName)
            target.config.save()
        }

        try {
            val record = RestoreRecordStore.get(source, packageName, source.userId)
            if (record != null) {
                RestoreRecordStore.put(target, packageName, target.userId, record)
                RestoreRecordStore.remove(source, packageName, source.userId)
            }
        } catch (e: Exception) {
            Log.w(TAG, "RestoreRecord migrate failed for $packageName", e)
        }
    }

    private fun moveGroupIcon(
        fileSystem: IFileSystem,
        sourceGroupPath: String,
        targetGroupPath: String,
        packageName: String,
    ) {
        val srcIcon = Paths.get(sourceGroupPath, "$packageName.png").absolutePathString()
        val dstIcon = Paths.get(targetGroupPath, "$packageName.png").absolutePathString()
        if (!fileSystem.exists(srcIcon)) return
        if (fileSystem.exists(dstIcon)) {
            fileSystem.delete(dstIcon)
        }
        if (!fileSystem.move(srcIcon, dstIcon)) {
            if (fileSystem.copyRecursively(srcIcon, dstIcon, true)) {
                fileSystem.delete(srcIcon)
            }
        }
    }

    private fun isCompletelyEmptyDir(fileSystem: IFileSystem, path: String): Boolean {
        if (!fileSystem.exists(path)) return true
        if (fileSystem.fileType(path) != IFileType.TYPE_DIR) return false
        val entries = fileSystem.listDir(path).orEmpty().filter { it != "." && it != ".." }
        return entries.isEmpty()
    }

    private fun collectFileEntries(fileSystem: IFileSystem, root: String): Map<String, Long> {
        val result = linkedMapOf<String, Long>()
        fun walk(dir: String, rel: String) {
            for (name in fileSystem.listDir(dir).orEmpty()) {
                if (name == "." || name == "..") continue
                val child = Paths.get(dir, name).absolutePathString()
                val childRel = if (rel.isEmpty()) name else "$rel/$name"
                when (fileSystem.fileType(child)) {
                    IFileType.TYPE_DIR -> walk(child, childRel)
                    IFileType.TYPE_FILE -> result[childRel] = fileSystem.length(child)
                }
            }
        }
        if (fileSystem.exists(root) && fileSystem.fileType(root) == IFileType.TYPE_DIR) {
            walk(root, "")
        }
        return result
    }

    private fun isCompletePackageDir(fileSystem: IFileSystem, path: String): Boolean {
        if (!fileSystem.exists(path) || fileSystem.fileType(path) != IFileType.TYPE_DIR) return false
        return !isCompletelyEmptyDir(fileSystem, path)
    }

    private fun isCompleteRelativeTo(
        fileSystem: IFileSystem,
        source: String,
        target: String,
    ): Boolean {
        val src = collectFileEntries(fileSystem, source)
        if (src.isEmpty()) return isCompletelyEmptyDir(fileSystem, target)
        val dst = collectFileEntries(fileSystem, target)
        for ((rel, size) in src) {
            if (dst[rel] != size) return false
        }
        return true
    }

    private fun isIncompleteRelativeTo(
        fileSystem: IFileSystem,
        source: String,
        target: String,
    ): Boolean {
        if (isCompletelyEmptyDir(fileSystem, target)) return true
        return !isCompleteRelativeTo(fileSystem, source, target)
    }

    fun deleteGroup(
        context: Context,
        fileSystem: IFileSystem,
        appManager: IAppManager,
        groupId: String,
        currentGroups: List<SnapGroup>,
        deleteFiles: Boolean = false,
        onComplete: (() -> Unit)? = null,
    ) {
        scope.launch {
            try {
                loadGroupsMutex.withLock {
                    val group = (loadedGroups.ifEmpty { currentGroups }).find { it.id == groupId }
                    GlobalConfig.groups = GlobalConfig.groups.toMutableList().apply {
                        remove(groupId)
                    }
                    GlobalConfig.archiveRoots = GlobalConfig.archiveRoots.filterNot {
                        it is ArchiveRoot.Group && it.groupId == groupId
                    }
                    group?.let {
                        it.config.mmkv.clearAll()
                        if (deleteFiles) {
                            fileSystem.delete(it.path)
                        }
                    }
                    reloadGroupsLocked(context, fileSystem, appManager)
                }
                withContext(Dispatchers.Main) {
                    onComplete?.invoke()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun isPathOccupiedBySet(path: String, excludeSetId: String? = null): Boolean {
        return GlobalConfig.groupSetIds.any { id ->
            id != excludeSetId &&
                GroupSetMembership.normalizePath(SnapGroupSet(id).path) == path
        } || loadedSets.any { set ->
            set.id != excludeSetId &&
                GroupSetMembership.normalizePath(set.path) == path
        }
    }

    private fun isPathOccupiedByGroup(path: String, excludeGroupId: String? = null): Boolean {
        return GlobalConfig.groups.any { id ->
            id != excludeGroupId &&
                GroupSetMembership.normalizePath(SnapGroup(id).path) == path
        } || loadedGroups.any { group ->
            group.id != excludeGroupId &&
                GroupSetMembership.normalizePath(group.path) == path
        }
    }

    private fun appendBasenameToSetOrder(set: SnapGroupSet, basename: String) {
        if (basename.isEmpty()) return
        val order = set.groupOrder.toMutableList()
        if (basename !in order) {
            order += basename
            set.groupOrder = order
            set.save()
        }
    }
}

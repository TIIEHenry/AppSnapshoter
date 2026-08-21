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
import tiiehenry.android.app.snapshot.group.ArchiveRoot
import tiiehenry.android.app.snapshot.group.GroupSetColors
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.group.SnapGroupSet
import tiiehenry.android.app.snapshot.main.launch.ArchiveListItem
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
        val existingGroups = groupList.value.orEmpty().associateBy { it.id }
        val groups = groupIds.map { groupId ->
            Log.i(TAG, "loadGroup: $groupId")
            (existingGroups[groupId] ?: SnapGroup(groupId)).apply {
                loadApps(context, fileSystem, appManager, true)
            }
        }
        val groupsById = groups.associateBy { it.id }

        val setIds = GlobalConfig.groupSetIds
        val existingSets = groupSetList.value.orEmpty().associateBy { it.id }
        val sets = setIds.map { setId ->
            existingSets[setId] ?: SnapGroupSet(setId)
        }
        val setsById = sets.associateBy { it.id }

        val membersBySetId = deriveLiveMembers(sets, groups)
        val memberGroupIds = membersBySetId.values.flatten().map { it.id }.toSet()

        val reconciled = ArchiveListProjector.reconcileRoots(
            roots = GlobalConfig.archiveRoots,
            allGroupIds = groupsById.keys,
            memberGroupIds = memberGroupIds,
            setIds = setsById.keys,
        )
        if (reconciled != GlobalConfig.archiveRoots) {
            GlobalConfig.archiveRoots = reconciled
        }

        val draft = ArchiveListProjector.project(
            ArchiveListProjector.Input(
                roots = reconciled,
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
        val archiveItems = materializeArchiveList(draft, setsById, groupsById, membersBySetId)

        groupList.postValue(groups)
        groupSetList.postValue(sets)
        archiveList.postValue(archiveItems)
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
                    ArchiveListItem.GroupCard(group, item.setId, accent)
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
    ) {
        scope.launch {
            try {
                loadGroupsMutex.withLock {
                    val normalizedPath = GroupSetMembership.normalizePath(path)
                    if (isPathOccupiedBySet(normalizedPath) || isPathOccupiedByGroup(normalizedPath)) {
                        Log.w(TAG, "addGroup rejected: path already used as set or group: $normalizedPath")
                        return@withLock
                    }
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
                    val belongingSet = groupSetList.value.orEmpty()
                        .firstOrNull { GroupSetMembership.normalizePath(it.path) == parent }
                    if (belongingSet == null) {
                        GlobalConfig.archiveRoots = GlobalConfig.archiveRoots + ArchiveRoot.Group(groupId)
                    } else {
                        appendBasenameToSetOrder(belongingSet, GroupSetMembership.basename(normalizedPath))
                    }
                    reloadGroupsLocked(context, fileSystem, appManager)
                }
            } catch (e: Exception) {
                Log.e(TAG, "addGroup failed", e)
            }
        }
    }

    fun addGroupSet(
        context: Context,
        fileSystem: IFileSystem,
        appManager: IAppManager,
        name: String,
        path: String,
        onComplete: ((discoveredCount: Int) -> Unit)? = null,
    ) {
        scope.launch {
            var discovered = 0
            try {
                loadGroupsMutex.withLock {
                    val normalizedPath = GroupSetMembership.normalizePath(path)
                    if (isPathOccupiedBySet(normalizedPath)) {
                        Log.w(TAG, "addGroupSet rejected: set path already exists")
                        return@withLock
                    }
                    if (isPathOccupiedByGroup(normalizedPath)) {
                        Log.w(TAG, "addGroupSet rejected: path equals an existing group; upgrade empty group first")
                        return@withLock
                    }
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
                    discovered = discoverGroupsLocked(fileSystem, set)
                    reloadGroupsLocked(context, fileSystem, appManager)
                }
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(discovered)
                }
            } catch (e: Exception) {
                Log.e(TAG, "addGroupSet failed", e)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(discovered)
                }
            }
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
            val g = groupList.value.orEmpty().find { it.id == id } ?: SnapGroup(id)
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
                    val set = (groupSetList.value.orEmpty().find { it.id == setId }
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
                    val set = groupSetList.value.orEmpty().find { it.id == setId }
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

    fun setGroupSetCollapsed(
        context: Context,
        fileSystem: IFileSystem,
        appManager: IAppManager,
        setId: String,
        collapsed: Boolean,
    ) {
        scope.launch {
            try {
                loadGroupsMutex.withLock {
                    val set = groupSetList.value.orEmpty().find { it.id == setId }
                        ?: SnapGroupSet(setId)
                    set.isCollapsed = collapsed
                    reloadGroupsLocked(context, fileSystem, appManager)
                }
            } catch (e: Exception) {
                Log.e(TAG, "setGroupSetCollapsed failed", e)
            }
        }
    }

    /** 一键折叠：所有分组集 Header + 所有分组卡片 body。 */
    fun collapseAllArchive(
        context: Context,
        fileSystem: IFileSystem,
        appManager: IAppManager,
    ) {
        scope.launch {
            try {
                loadGroupsMutex.withLock {
                    for (set in groupSetList.value.orEmpty()) {
                        set.isCollapsed = true
                    }
                    for (group in groupList.value.orEmpty()) {
                        group.isCollapsed = true
                    }
                    reloadGroupsLocked(context, fileSystem, appManager)
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
                    val set = groupSetList.value.orEmpty().find { it.id == setId }
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
        onComplete: (() -> Unit)? = null,
    ) {
        scope.launch {
            try {
                loadGroupsMutex.withLock {
                    val group = groupList.value.orEmpty().find { it.id == groupId }
                        ?: SnapGroup(groupId)
                    val oldPath = GroupSetMembership.normalizePath(group.path)
                    val oldBase = GroupSetMembership.basename(oldPath)
                    val normalized = GroupSetMembership.normalizePath(newPath)
                    val newBase = GroupSetMembership.basename(normalized)
                    group.path = normalized
                    if (newName != null) group.name = newName
                    if (userId != null) group.userId = userId
                    group.config.save()

                    // Same set, basename changed → rewrite groupOrder
                    if (oldBase != newBase) {
                        for (set in groupSetList.value.orEmpty()) {
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
                }
                withContext(Dispatchers.Main) { onComplete?.invoke() }
            } catch (e: Exception) {
                Log.e(TAG, "updateGroupPath failed", e)
                withContext(Dispatchers.Main) { onComplete?.invoke() }
            }
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
        onComplete: (() -> Unit)? = null,
    ) {
        scope.launch {
            try {
                loadGroupsMutex.withLock {
                    val set = groupSetList.value.orEmpty().find { it.id == setId }
                        ?: SnapGroupSet(setId)
                    set.path = GroupSetMembership.normalizePath(newPath)
                    if (newName != null) set.name = newName
                    if (accentColor != null) set.accentColor = accentColor
                    set.save()
                    discoverGroupsLocked(fileSystem, set)
                    reloadGroupsLocked(context, fileSystem, appManager)
                }
                withContext(Dispatchers.Main) { onComplete?.invoke() }
            } catch (e: Exception) {
                Log.e(TAG, "updateGroupSetPath failed", e)
                withContext(Dispatchers.Main) { onComplete?.invoke() }
            }
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
                    val group = groupList.value.orEmpty().find { it.id == groupId }
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
        onComplete: (() -> Unit)? = null
    ) {
        scope.launch {
            try {
                loadGroupsMutex.withLock {
                    val group = (groupList.value ?: currentGroups)
                        .find { it.id == groupId } ?: return@launch
                    for (appInfo in appInfos) {
                        val packageName = appInfo.packageName
                        Log.d("addAppsToGroup", "Adding app: $packageName to group: ${group.id}")
                        val packageDir = Paths.get(group.path, packageName).absolutePathString()
                        if (!fileSystem.exists(packageDir)) {
                            fileSystem.mkdirs(packageDir)
                        }

                        val iconFile = Paths.get(group.path, "$packageName.png").absolutePathString()
                        AppIconUtils.loadAndSaveAppIcon(
                            context,
                            fileSystem,
                            appManager,
                            packageName,
                            0,
                            iconFile
                        )
                    }
                    reloadGroupsLocked(context, fileSystem, appManager)
                }
                withContext(Dispatchers.Main) {
                    onComplete?.invoke()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("addAppsToGroup", "Error: ${e.message}", e)
            }
        }
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
                    val group = (groupList.value ?: currentGroups).find { it.id == groupId }
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

    private fun isPathOccupiedBySet(path: String): Boolean {
        return GlobalConfig.groupSetIds.any {
            GroupSetMembership.normalizePath(SnapGroupSet(it).path) == path
        } || groupSetList.value.orEmpty().any {
            GroupSetMembership.normalizePath(it.path) == path
        }
    }

    private fun isPathOccupiedByGroup(path: String): Boolean {
        return GlobalConfig.groups.any {
            GroupSetMembership.normalizePath(SnapGroup(it).path) == path
        } || groupList.value.orEmpty().any {
            GroupSetMembership.normalizePath(it.path) == path
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

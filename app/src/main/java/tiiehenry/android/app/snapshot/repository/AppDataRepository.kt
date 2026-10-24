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
import tiiehenry.android.app.snapshot.group.UninstallAppResult
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.group.SnapGroupSet
import tiiehenry.android.app.snapshot.instance.InstanceCatalog
import tiiehenry.android.app.snapshot.main.launch.ArchiveListItem
import tiiehenry.android.app.snapshot.main.launch.archiveAppsFingerprint
import tiiehenry.android.app.snapshot.main.launch.batch.RestoreRecordStore
import tiiehenry.android.app.snapshot.utils.AppIconUtils
import tiiehenry.android.snapshot.app.IAppManager
import tiiehenry.android.snapshot.app.UserInfoHide
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.fs.IFileType
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
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
    private val loadAppsMutex = Mutex()
    private val reconciledInstanceDirs = ConcurrentHashMap.newKeySet<String>()

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

    /**
     * 是否已成功拉取过用户列表。与 [appsList] 初值 `emptyMap()` 区分「尚未加载」。
     */
    @Volatile
    private var appsCatalogLoaded: Boolean = false
    val isAppsCatalogLoaded = MutableLiveData(false)

    fun loadData(context: Context, fileSystem: IFileSystem, appManager: IAppManager) {
        scope.launch {
            isAppsLoading.postValue(true)
            val groups = loadGroups(context, fileSystem, appManager)
            reconcileInstances(fileSystem, groups)
            loadApps(fileSystem, appManager)
        }
    }

    fun scheduleLoadGroups(context: Context, fileSystem: IFileSystem, appManager: IAppManager) {
        scope.launch {
            val groups = loadGroups(context, fileSystem, appManager)
            reconcileInstances(fileSystem, groups)
        }
    }

    /**
     * Recover instance directories left behind by a process death during a
     * commit. This is a write path, so it must hold [packageOpGuard] for every
     * package directory it touches; a busy directory is retried on the next
     * group load instead of being skipped forever.
     */
    private suspend fun reconcileInstances(
        fileSystem: IFileSystem,
        groups: List<SnapGroup>,
    ) {
        val liveDirs = groups.flatMap { group ->
            synchronized(group.apps) { group.apps.map { it.packageDir } }
        }.toSet()
        // A removed group can later be re-registered with the same path; drop
        // stale marks so its instances are reconciled again.
        reconciledInstanceDirs.retainAll(liveDirs)
        val dirs = liveDirs.filter { it !in reconciledInstanceDirs }
        if (dirs.isEmpty()) return
        withContext(Dispatchers.IO) {
            for (dir in dirs) {
                if (packageOpGuard.isGlobalBatchRunning() || !packageOpGuard.tryBeginPackageOp(dir)) {
                    continue
                }
                try {
                    // Only mark a directory as reconciled when every slot is clean.
                    // A failed metadata recovery must be retried on the next load.
                    if (InstanceCatalog(fileSystem, dir).reconcileAll()) {
                        reconciledInstanceDirs.add(dir)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "instance reconcile failed: $dir", e)
                } finally {
                    packageOpGuard.endPackageOp(dir)
                }
            }
        }
    }

    fun refreshGroup(
        context: Context,
        fileSystem: () -> IFileSystem,
        appManager: () -> IAppManager,
        groupId: String,
    ) {
        scope.launch {
            try {
                val fs = fileSystem()
                val manager = appManager()
                loadGroupsMutex.withLock {
                    if (loadedGroups.none { it.id == groupId }) return@withLock
                    // Scan a detached model so UI readers neither wait on its lock nor see a partial list.
                    val group = SnapGroup(groupId)
                    group.loadApps(context, fs, manager, true)
                    loadedGroups = loadedGroups.map { if (it.id == groupId) group else it }
                    groupList.postValue(loadedGroups.toList())
                    reprojectArchiveListLocked()
                }
            } catch (e: Exception) {
                Log.e(TAG, "refreshGroup failed: $groupId", e)
            }
        }
    }

    fun scheduleLoadApps(
        fileSystem: () -> IFileSystem,
        appManager: () -> IAppManager,
    ) {
        scope.launch {
            isAppsLoading.postValue(true)
            try {
                loadApps(fileSystem(), appManager())
            } catch (e: Exception) {
                Log.e(TAG, "scheduleLoadApps failed", e)
                isAppsLoading.postValue(false)
            }
        }
    }

    fun uninstallInstalledApp(
        fileSystem: () -> IFileSystem,
        appManager: () -> IAppManager,
        packageName: String,
        userId: Int,
        onComplete: (UninstallAppResult) -> Unit,
    ) {
        scope.launch {
            if (packageOpGuard.isGlobalBatchRunning() || packageOpGuard.isBusy()) {
                withContext(Dispatchers.Main) { onComplete(UninstallAppResult.Busy) }
                return@launch
            }
            val ok = appManager().uninstallApk(packageName, userId)
            if (ok) {
                isAppsLoading.postValue(true)
                loadApps(fileSystem(), appManager())
                withContext(Dispatchers.Main) { onComplete(UninstallAppResult.Success) }
            } else {
                withContext(Dispatchers.Main) { onComplete(UninstallAppResult.Failed) }
            }
        }
    }

    suspend fun loadGroups(context: Context, fileSystem: IFileSystem, appManager: IAppManager): List<SnapGroup> {
        return loadGroupsMutex.withLock {
            reloadGroupsLocked(context, fileSystem, appManager)
            loadedGroups
        }
    }

    /** 已加载分组的最新快照；等待进行中的 loadGroups 完成，避免读到 postValue 的旧值。 */
    suspend fun loadedGroupsSnapshot(): List<SnapGroup> = loadGroupsMutex.withLock { loadedGroups }

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
        archiveList.postValue(projectArchiveListLocked())
    }

    /** 必须在 [loadGroupsMutex] 内调用。只生成列表，不写 [archiveList]。 */
    private fun projectArchiveListLocked(): List<ArchiveListItem> {
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
        return materializeArchiveList(draft, setsById, groupsById, membersBySetId)
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
                        name = group.name,
                        appsFingerprint = archiveAppsFingerprint(group),
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
        loadAppsMutex.withLock {
            Log.i(TAG, "loadApps")
            isAppsLoading.postValue(true)
            var succeeded = false
            try {
                val catalog = withContext(Dispatchers.IO) {
                    val userInfos = appManager.users ?: listOf()
                    Log.i(TAG, "loadApps: userInfos $userInfos")
                    scanInstalledCatalog(
                        users = userInfos,
                        userId = { it.id },
                        installedPackages = { user ->
                            appManager.getInstalledPackages(0, user.id)
                        },
                        readPackage = { user, packageName ->
                            val packageInfo = appManager.getPackageInfo(packageName, 0, user.id)
                            CatalogPackage(
                                packageName = packageName,
                                versionName = packageInfo?.versionName,
                                versionCode = packageInfo?.longVersionCode ?: 0L,
                            )
                        },
                        onUserFailure = { userId, error ->
                            Log.e(TAG, "Failed to load apps for user $userId", error)
                        },
                        onPackageFailure = { error -> error.printStackTrace() },
                    )
                }
                val rows = installedCatalogToPublish(catalog)
                if (rows != null) {
                    val appsMap = rows.mapValues { (user, packages) ->
                        packages.map { row ->
                            AppInfo(
                                fs = fileSystem,
                                appManager = appManager,
                                packageName = row.packageName,
                                userId = user.id,
                                versionName = row.versionName,
                                versionCode = row.versionCode,
                            )
                        }
                    }
                    appsList.postValue(appsMap)
                    markAppsCatalogLoaded()
                    succeeded = true
                } else if (catalog.noUsers) {
                    Log.e(TAG, "loadApps: no users, catalog not ready")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (!succeeded && !appsCatalogLoaded) {
                    Log.w(TAG, "loadApps: catalog still unloaded")
                }
                isAppsLoading.postValue(false)
            }
        }
    }

    private fun markAppsCatalogLoaded() {
        appsCatalogLoaded = true
        isAppsCatalogLoaded.postValue(true)
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
                            group.config.registerPath(normalizedPath, name, userId)

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
     * 列目录或子项 stat 失败时不改登记、不清 MMKV、不改 groupOrder、不 save。
     */
    private fun discoverGroupsLocked(fileSystem: IFileSystem, set: SnapGroupSet): Int {
        val setPath = GroupSetMembership.normalizePath(set.path)
        val groupIds = GlobalConfig.groups
        val pathsById = groupIds.associateWith { id ->
            (loadedGroups.find { it.id == id } ?: SnapGroup(id)).path
        }
        val result = discoverGroupSetMembers(
            setPath = setPath,
            groups = groupIds,
            pathsById = pathsById,
            groupOrder = set.groupOrder,
            listDir = { fileSystem.listDir(it) },
            fileType = { fileSystem.fileType(it) },
            groupJsonExists = { fileSystem.exists(it) },
        )
        if (!result.committed) {
            Log.e(TAG, "discoverGroups aborted; registration kept: ${result.failure}")
            return 0
        }

        for ((id, dir) in result.added) {
            val group = SnapGroup(id)
            group.path = dir.path
            if (dir.hasGroupJson) {
                group.config.load()
            } else {
                group.name = dir.basename
                group.config.save()
            }
        }
        GlobalConfig.groups = result.groups
        for (id in result.removedIds) {
            SnapGroup(id).config.mmkv.clearAll()
        }
        set.groupOrder = result.groupOrder
        set.save()

        val memberIds = result.groups.filter { id ->
            val path = result.added.firstOrNull { it.first == id }?.second?.path
                ?: pathsById[id]
                ?: return@filter false
            GroupSetMembership.isMemberOf(GroupSetMembership.normalizePath(path), setPath)
        }.toSet()
        GlobalConfig.archiveRoots = GlobalConfig.archiveRoots.filterNot {
            it is ArchiveRoot.Group && it.groupId in memberIds
        }
        return result.newCount
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
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        scope.launch {
            val success = try {
                loadGroupsMutex.withLock {
                    val set = loadedSets.find { it.id == setId }
                        ?: SnapGroupSet(setId)
                    val setPath = GroupSetMembership.normalizePath(set.path)
                    val memberIds = GlobalConfig.groups.filter { id ->
                        GroupSetMembership.isMemberOf(SnapGroup(id).path, setPath)
                    }
                    val plan = planGroupSetDeletion(
                        mode = mode,
                        setId = setId,
                        memberIds = memberIds,
                        groups = GlobalConfig.groups,
                        archiveRoots = GlobalConfig.archiveRoots,
                        deleteSetDirectory = { fileSystem.delete(set.path) },
                    )
                    if (!plan.success) return@withLock false
                    GlobalConfig.groups = plan.groups
                    plan.clearMemberIds.forEach { SnapGroup(it).config.mmkv.clearAll() }
                    GlobalConfig.archiveRoots = plan.archiveRoots
                    set.config.clearLocal()
                    reloadGroupsLocked(context, fileSystem, appManager)
                    true
                }
            } catch (e: Exception) {
                Log.e(TAG, "deleteGroupSet failed", e)
                false
            }
            withContext(Dispatchers.Main) { onComplete?.invoke(success) }
        }
    }

    /**
     * 跳进分组前，在锁内展开所属集和分组并投影。
     * 释放锁后于主线程先写 [archiveList]，再调用 [writeNavigate]。
     * 找不到分组时不改列表，仍写出导航事件。
     */
    fun expandArchiveForNavigation(groupId: String, writeNavigate: (String) -> Unit) {
        scope.launch {
            val projected = try {
                loadGroupsMutex.withLock {
                    expandThenProject(
                        groupId = groupId,
                        groups = loadedGroups,
                        sets = loadedSets,
                        groupIdOf = { it.id },
                        groupPathOf = { it.path },
                        markGroupExpanded = { it.isCollapsed = false },
                        setPathOf = { it.path },
                        markSetExpanded = { it.isCollapsed = false },
                        project = { projectArchiveListLocked() },
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "expandArchiveForNavigation failed", e)
                null
            }
            withContext(Dispatchers.Main) {
                emitNavigateAfterArchiveList(
                    groupId = groupId,
                    projected = projected,
                    writeArchiveList = { archiveList.value = it },
                    writeNavigate = writeNavigate,
                )
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

            val result = transferPackageDirectory(
                fileSystem,
                sourceDir!!,
                targetDir!!,
            ) {
                moveGroupIcon(fileSystem, source.path, target.path, packageName)
                loadGroupsMutex.withLock {
                    finalizeMoveMetadata(source, target, packageName)
                    reloadGroupsLocked(context, fileSystem, appManager)
                }
            }
            if (result is MoveAppResult.Error && result.message.contains("source delete failed")) {
                Log.w(TAG, "failed to delete source: $sourceDir")
            }
            return result
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

    private fun isCompletePackageDir(fileSystem: IFileSystem, path: String): Boolean {
        if (!fileSystem.exists(path) || fileSystem.fileType(path) != IFileType.TYPE_DIR) return false
        return !isCompletelyEmptyDir(fileSystem, path)
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
                val removed = loadGroupsMutex.withLock {
                    val group = (loadedGroups.ifEmpty { currentGroups }).find { it.id == groupId }
                        ?: return@withLock false
                    val originalPath = group.path
                    val success = removeRegisteredGroup(
                        path = originalPath,
                        deleteFiles = deleteFiles,
                        delete = fileSystem::delete,
                    ) {
                        GlobalConfig.groups = GlobalConfig.groups.filterNot { it == groupId }
                        GlobalConfig.archiveRoots = GlobalConfig.archiveRoots.filterNot {
                            it is ArchiveRoot.Group && it.groupId == groupId
                        }
                        group.config.mmkv.clearAll()
                    }
                    if (!success) {
                        Log.w(TAG, "deleteGroup failed; registration retained: $originalPath")
                        return@withLock false
                    }
                    reloadGroupsLocked(context, fileSystem, appManager)
                    true
                }
                if (removed) withContext(Dispatchers.Main) {
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

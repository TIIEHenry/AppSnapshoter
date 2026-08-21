package tiiehenry.android.app.snapshot

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.group.ArchiveRoot
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.group.SnapGroupSet
import tiiehenry.android.app.snapshot.main.launch.ArchiveListItem
import tiiehenry.android.app.snapshot.repository.AppDataRepository
import tiiehenry.android.app.snapshot.repository.PathRegistrationResult
import tiiehenry.android.snapshot.app.UserInfoHide

/**
 * SnapshotViewModel - 保留以兼容现有代码
 * 所有业务逻辑已迁移至 [AppDataRepository]
 * 异步任务使用 repository 进程级协程作用域，避免 Application 单例 ViewModel 的 viewModelScope 失效。
 */
class SnapshotViewModel : ViewModel() {

    companion object {
        const val TAG = "SnapShotViewModel"
    }

    private val repository = AppDataRepository.getInstance()

    val groupList: MutableLiveData<List<SnapGroup>> get() = repository.groupList
    val groupSetList: MutableLiveData<List<SnapGroupSet>> get() = repository.groupSetList
    /** 存档 Tab SSOT；见 [AppDataRepository.archiveList]。 */
    val archiveList: MutableLiveData<List<ArchiveListItem>> get() = repository.archiveList
    val appsList: MutableLiveData<Map<UserInfoHide, List<AppInfo>>> get() = repository.appsList
    /** 应用 catalog 加载态；见 [AppDataRepository.isAppsLoading]。 */
    val isAppsLoading: MutableLiveData<Boolean> get() = repository.isAppsLoading

    /** Event: timeline / apps 跳转存档 Tab 的分组 */
    val navigateToGroup = MutableLiveData<String?>(null)

    /** 与 [navigateToGroup] 配套：滚到组内该包名（可空） */
    @Volatile
    var pendingNavigatePackage: String? = null
        private set

    /** Event: scroll to SetHeader；不强制展开 */
    val navigateToGroupSet = MutableLiveData<String?>(null)

    /** Global mutex for batch archive/restore across archive and timeline tabs */
    val isBatchRunning = MutableLiveData(false)

    fun tryBeginBatchOperation(): Boolean {
        if (!repository.packageOpGuard.tryBeginGlobalBatch()) return false
        isBatchRunning.value = true
        return true
    }

    fun endBatchOperation() {
        repository.packageOpGuard.endGlobalBatch()
        isBatchRunning.value = false
    }

    /** 从 groupList 解析当前 SnapGroup，避免 ViewHolder 闭包持有 stale 实例 */
    fun resolveGroup(groupId: String, fallback: SnapGroup? = null): SnapGroup? =
        groupList.value?.find { it.id == groupId } ?: fallback

    fun resolveGroupSet(setId: String, fallback: SnapGroupSet? = null): SnapGroupSet? =
        groupSetList.value?.find { it.id == setId } ?: fallback

    private fun appDeps() = SnapshotApp.getInstance().let {
        Triple(SnapshotApp.getContext(), it.fileSystem, it.appManager)
    }

    fun loadData() {
        val (context, fileSystem, appManager) = appDeps()
        repository.loadData(context, fileSystem, appManager)
    }

    fun loadGroups() {
        val (context, fileSystem, appManager) = appDeps()
        repository.scheduleLoadGroups(context, fileSystem, appManager)
    }

    fun addGroup(
        name: String,
        path: String,
        userId: Int = 0,
        onComplete: ((PathRegistrationResult) -> Unit)? = null,
    ) {
        val (context, fileSystem, appManager) = appDeps()
        repository.addGroup(context, fileSystem, appManager, name, path, userId, onComplete)
    }

    fun addGroupSet(
        name: String,
        path: String,
        onComplete: ((PathRegistrationResult) -> Unit)? = null,
    ) {
        val (context, fileSystem, appManager) = appDeps()
        repository.addGroupSet(context, fileSystem, appManager, name, path, onComplete)
    }

    fun refreshGroupSet(setId: String, onComplete: ((Int) -> Unit)? = null) {
        val (context, fileSystem, appManager) = appDeps()
        repository.refreshGroupSet(context, fileSystem, appManager, setId, onComplete)
    }

    fun deleteGroupSet(
        setId: String,
        mode: AppDataRepository.DeleteGroupSetMode = AppDataRepository.DeleteGroupSetMode.SET_ONLY,
        onComplete: (() -> Unit)? = null,
    ) {
        val (context, fileSystem, appManager) = appDeps()
        repository.deleteGroupSet(context, fileSystem, appManager, setId, mode, onComplete)
    }

    fun setGroupSetCollapsed(setId: String, collapsed: Boolean) {
        val (context, fileSystem, appManager) = appDeps()
        repository.setGroupSetCollapsed(context, fileSystem, appManager, setId, collapsed)
    }

    fun collapseAllArchive() {
        val (context, fileSystem, appManager) = appDeps()
        repository.collapseAllArchive(context, fileSystem, appManager)
    }

    fun saveArchiveRootsOrder(roots: List<ArchiveRoot>, onComplete: (() -> Unit)? = null) {
        val (context, fileSystem, appManager) = appDeps()
        repository.saveArchiveRootsOrder(context, fileSystem, appManager, roots, onComplete)
    }

    fun saveGroupSetOrder(setId: String, basenames: List<String>, onComplete: (() -> Unit)? = null) {
        val (context, fileSystem, appManager) = appDeps()
        repository.saveGroupSetOrder(context, fileSystem, appManager, setId, basenames, onComplete)
    }

    fun updateGroupPath(
        groupId: String,
        newPath: String,
        newName: String? = null,
        userId: Int? = null,
        onComplete: ((PathRegistrationResult) -> Unit)? = null,
    ) {
        val (context, fileSystem, appManager) = appDeps()
        repository.updateGroupPath(
            context, fileSystem, appManager, groupId, newPath, newName, userId, onComplete
        )
    }

    fun updateGroupSetPath(
        setId: String,
        newPath: String,
        newName: String? = null,
        accentColor: Int? = null,
        onComplete: ((PathRegistrationResult) -> Unit)? = null,
    ) {
        val (context, fileSystem, appManager) = appDeps()
        repository.updateGroupSetPath(
            context, fileSystem, appManager, setId, newPath, newName, accentColor, onComplete
        )
    }

    fun upgradeEmptyGroupToSet(groupId: String, setName: String, onComplete: ((Int) -> Unit)? = null) {
        val (context, fileSystem, appManager) = appDeps()
        repository.upgradeEmptyGroupToSet(context, fileSystem, appManager, groupId, setName, onComplete)
    }

    fun addAppsToGroup(
        groupId: String,
        appInfos: List<AppInfo>,
        callback: (tiiehenry.android.app.snapshot.group.AddAppsResult) -> Unit,
    ) {
        val (context, fileSystem, appManager) = appDeps()
        repository.addAppsToGroup(
            context = context,
            fileSystem = fileSystem,
            appManager = appManager,
            groupId = groupId,
            currentGroups = groupList.value ?: emptyList(),
            appInfos = appInfos,
            onComplete = callback,
        )
    }

    fun moveAppBetweenGroups(
        fromGroupId: String,
        toGroupId: String,
        packageName: String,
        callback: (tiiehenry.android.app.snapshot.group.MoveAppResult) -> Unit,
    ) {
        val (context, fileSystem, appManager) = appDeps()
        repository.moveAppBetweenGroups(
            context, fileSystem, appManager, fromGroupId, toGroupId, packageName, callback
        )
    }

    fun setMembershipMode(
        groupId: String,
        mode: tiiehenry.android.app.snapshot.group.GroupMembershipMode,
        callback: (tiiehenry.android.app.snapshot.group.SetMembershipModeResult) -> Unit,
    ) {
        val (context, fileSystem, appManager) = appDeps()
        repository.setMembershipMode(context, fileSystem, appManager, groupId, mode, callback)
    }

    fun deleteGroup(groupId: String, deleteFiles: Boolean = false) {
        val (context, fileSystem, appManager) = appDeps()
        repository.deleteGroup(
            context = context,
            fileSystem = fileSystem,
            appManager = appManager,
            groupId = groupId,
            currentGroups = groupList.value ?: emptyList(),
            deleteFiles = deleteFiles,
        )
    }

    /** 时间线/应用 Tab 跳转：若所属集折叠则先展开再 pending 滚到 GroupCard；可选滚到组内应用 */
    fun requestNavigateToGroup(groupId: String, packageName: String? = null) {
        pendingNavigatePackage = packageName
        val group = resolveGroup(groupId) ?: run {
            navigateToGroup.value = groupId
            return
        }
        val set = groupSetList.value.orEmpty().firstOrNull { set ->
            tiiehenry.android.app.snapshot.repository.GroupSetMembership.isMemberOf(group.path, set.path)
        }
        if (set != null && set.isCollapsed) {
            setGroupSetCollapsed(set.id, collapsed = false)
        }
        if (group.isCollapsed) {
            group.isCollapsed = false
        }
        navigateToGroup.value = groupId
    }

    fun consumePendingNavigatePackage(): String? {
        val pkg = pendingNavigatePackage
        pendingNavigatePackage = null
        return pkg
    }
}

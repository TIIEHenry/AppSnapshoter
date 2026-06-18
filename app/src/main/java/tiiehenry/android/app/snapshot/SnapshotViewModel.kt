package tiiehenry.android.app.snapshot

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.repository.AppDataRepository
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
    val appsList: MutableLiveData<Map<UserInfoHide, List<AppInfo>>> get() = repository.appsList
    val isAppsLoading: MutableLiveData<Boolean> get() = repository.isAppsLoading

    /** Event: timeline requests scrolling to a specific group in the archive tab */
    val navigateToGroup = MutableLiveData<String?>(null)

    /** Global mutex for batch archive/restore across archive and timeline tabs */
    val isBatchRunning = MutableLiveData(false)

    fun tryBeginBatchOperation(): Boolean {
        if (isBatchRunning.value == true) return false
        isBatchRunning.value = true
        return true
    }

    fun endBatchOperation() {
        isBatchRunning.value = false
    }

    /** 从 groupList 解析当前 SnapGroup，避免 ViewHolder 闭包持有 stale 实例 */
    fun resolveGroup(groupId: String, fallback: SnapGroup? = null): SnapGroup? =
        groupList.value?.find { it.id == groupId } ?: fallback

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

    fun addGroup(name: String, path: String, userId: Int = 0) {
        val (context, fileSystem, appManager) = appDeps()
        repository.addGroup(context, fileSystem, appManager, name, path, userId)
    }

    fun addAppsToGroup(groupId: String, appInfos: List<AppInfo>, callback: () -> Unit) {
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
}

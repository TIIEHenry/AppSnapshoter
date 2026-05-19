package tiiehenry.android.app.snapshot

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.repository.AppDataRepository
import tiiehenry.android.snapshot.app.UserInfoHide

/**
 * SnapshotViewModel - 保留以兼容现有代码
 * 所有业务逻辑已迁移至 [AppDataRepository]
 * TODO: 后续改为通过 ViewModelProvider 标准创建，使用 viewModelScope
 */
class SnapshotViewModel : ViewModel() {

    companion object {
        const val TAG = "SnapShotViewModel"
    }

    private val repository = AppDataRepository.getInstance()

    val groupList: MutableLiveData<List<SnapGroup>> get() = repository.groupList
    val appsList: MutableLiveData<Map<UserInfoHide, List<AppInfo>>> get() = repository.appsList
    val isAppsLoading: MutableLiveData<Boolean> get() = repository.isAppsLoading

    fun loadData() {
        val app = SnapshotApp.getInstance()
        repository.loadData(
            SnapshotApp.getContext(),
            app.fileSystem,
            app.appManager
        )
    }

    fun loadGroups() {
        val app = SnapshotApp.getInstance()
        viewModelScope.launch {
            repository.loadGroups(
                SnapshotApp.getContext(),
                app.fileSystem,
                app.appManager
            )
        }
    }

    fun addGroup(name: String, path: String, userId: Int = 0) {
        repository.addGroup(name, path, userId) {
            loadGroups()
        }
    }

    fun addAppsToGroup(groupId: String, appInfos: List<AppInfo>, callback: () -> Unit) {
        val app = SnapshotApp.getInstance()
        repository.addAppsToGroup(
            context = SnapshotApp.getContext(),
            fileSystem = app.fileSystem,
            appManager = app.appManager,
            groupId = groupId,
            currentGroups = groupList.value ?: emptyList(),
            appInfos = appInfos,
            onComplete = callback
        )
    }

    fun deleteGroup(groupId: String, deleteFiles: Boolean = false) {
        val app = SnapshotApp.getInstance()
        repository.deleteGroup(
            fileSystem = app.fileSystem,
            groupId = groupId,
            currentGroups = groupList.value ?: emptyList(),
            deleteFiles = deleteFiles,
            onComplete = {
                loadGroups()
            }
        )
    }
}

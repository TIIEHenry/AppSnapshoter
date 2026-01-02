package tiieherny.android.app.snapshotor

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiieherny.android.app.snapshotor.app.SnapApp
import tiieherny.android.app.snapshotor.config.GlobalConfig
import tiieherny.android.app.snapshotor.group.SnapGroup
import tiiehenry.android.shapshotor.app.IAppManager
import tiiehenry.android.shapshotor.provider.appmanager.AppManagerProviderImpl
import java.util.UUID

class SnapShotViewModel : ViewModel() {

    val appManager: IAppManager by lazy {
        val context = SnapShotApp.getContext()
        AppManagerProviderImpl(context, context).provide()
    }

    val groupList = MutableLiveData<List<SnapGroup>>()
    val appList = MutableLiveData<List<SnapApp>>()

    fun loadData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                loadGroups()
                loadApps()
            }
        }
    }

    private fun loadGroups() {
        val groupIds = GlobalConfig.groups
        val groups = groupIds.map { groupId ->
            val mmkv = MMKV.mmkvWithID(groupId)
            SnapGroup(
                id = groupId,
                mmkv = mmkv,
            )
        }
        groupList.postValue(groups)
    }

    private fun loadApps() {
        // TODO: 从系统加载已安装应用列表
        val apps = mutableListOf<SnapApp>()
        appList.postValue(apps)
    }

    fun addGroup(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // 生成唯一ID
                val groupId = UUID.randomUUID().toString()
                
                // 创建分组的MMKV实例
                val mmkv = MMKV.mmkvWithID(groupId)
                mmkv.encode("name", name)
                
                // 保存到全局配置
                val currentGroups = GlobalConfig.groups.toMutableSet()
                currentGroups.add(groupId)
                GlobalConfig.groups = currentGroups
                
                // 重新加载分组列表
                loadGroups()
            }
        }
    }
}

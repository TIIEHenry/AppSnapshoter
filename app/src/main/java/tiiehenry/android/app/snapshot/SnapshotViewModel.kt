package tiiehenry.android.app.snapshot

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.config.GlobalConfig
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.utils.AppIconUtils
import tiiehenry.android.snapshot.app.UserInfoParcelable
import java.io.ByteArrayOutputStream
import java.nio.file.Paths
import java.util.UUID
import kotlin.io.path.absolutePathString

class SnapshotViewModel : ViewModel() {
    companion object {
        const val TAG = "SnapShotViewModel"
    }

    val groupList = MutableLiveData<List<SnapGroup>>()

    /**
     * UserInfoParcelable to appInfo list
     */
    val appsList = MutableLiveData<Map<UserInfoParcelable, List<AppInfo>>>(emptyMap())

    /**
     * 应用列表加载状态
     */
    val isAppsLoading = MutableLiveData<Boolean>(false)

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            loadGroups()
            loadApps()
        }
    }

    fun loadGroups() {
        Log.i(TAG, "loadGroups")
        val groupIds = GlobalConfig.groups
        val groups = groupIds.map { groupId ->
            Log.i(TAG, "loadGroup: $groupId")
            SnapGroup(groupId).apply {
                loadApps(
                    SnapshotApp.getContext(),
                    SnapshotApp.getInstance().fileSystem,
                    SnapshotApp.getInstance().appManager,
                    true
                )
            }
        }
        groupList.postValue(groups)
    }

    private fun loadApps() {
        Log.i(TAG, "loadApps")
        isAppsLoading.postValue(true)
        try {
            // 从系统加载已安装应用列表
            val appManager = SnapshotApp.getInstance().appManager
            val appsMap = mutableMapOf<UserInfoParcelable, List<AppInfo>>()

            // 获取所有用户列表
            val userInfos = appManager.users ?: listOf()

            Log.i(TAG, "loadApps: userInfos ${userInfos}")
            // 遍历每个用户获取应用列表
            for (userInfo in userInfos) {
                val userId = userInfo.id
                try {
                    val packageNames = appManager.getInstalledPackages(0, userId) ?: emptyList()
                    val apps = packageNames.mapNotNull { packageName ->
                        try {
                            val packageInfo = appManager.getPackageInfo(packageName, 0, userId)
                            val appInfo = AppInfo(
                                fs = SnapshotApp.getInstance().fileSystem,
                                appManager = appManager,
                                packageName = packageName,
                                userId = userId,
                                versionName = packageInfo?.versionName,
                                versionCode = packageInfo?.longVersionCode ?: 0
                            )
                            appInfo
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

            appsList.postValue(appsMap)
        } catch (e: Exception) {
            e.printStackTrace()
            appsList.postValue(emptyMap())
        } finally {
            isAppsLoading.postValue(false)
        }
    }

    fun addGroup(name: String, path: String, userId: Int = 0) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // 生成唯一ID
                val groupId = UUID.randomUUID().toString().substring(0, 7)

                val group = SnapGroup(groupId)
                group.path = path
                group.name = name
                group.config.groupConfigData.userId = userId
                group.config.save()

                // 保存到全局配置
                val currentGroups = GlobalConfig.groups.toMutableList()
                currentGroups.add(groupId)
                GlobalConfig.groups = currentGroups

                // 重新加载分组列表
                loadGroups()
            }
        }
    }

    fun addAppsToGroup(groupId: String, appInfos: List<AppInfo>, callback: () -> Unit) {
        viewModelScope.launch {
            try {
                // 获取分组
                val group = groupList.value?.find { it.id == groupId } ?: return@launch
                for (appInfo in appInfos) {
                    val packageName = appInfo.packageName
                    android.util.Log.d(
                        "addAppsToGroup",
                        "Adding app: $packageName to group: ${group.id}"
                    )
                    // 创建应用目录
                    val packageDir = Paths.get(group.path, packageName).absolutePathString()
                    if (!SnapshotApp.getInstance().fileSystem.exists(packageDir)) {
                        SnapshotApp.getInstance().fileSystem.mkdirs(packageDir)
                    }

                    // 保存应用图标
                    val iconFile =
                        Paths.get(group.path, "$packageName.png").absolutePathString()
                    AppIconUtils.loadAndSaveAppIcon(
                        SnapshotApp.getContext(),
                        SnapshotApp.getInstance().fileSystem,
                        SnapshotApp.getInstance().appManager,
                        packageName,
                        0,
                        iconFile
                    )
                }
                // 重新加载分组的应用列表
                group.loadApps(
                    SnapshotApp.getContext(),
                    SnapshotApp.getInstance().fileSystem,
                    SnapshotApp.getInstance().appManager,
                    true
                )
                android.util.Log.d("addAppsToGroup", "Loaded ${group.apps.size} apps")

                // 通知 UI 更新
//                loadGroups()
                withContext(Dispatchers.Main) {
                    callback()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("addAppsToGroup", "Error: ${e.message}", e)
            }
        }
    }


    fun deleteGroup(groupId: String, deleteFiles: Boolean = false) {
        viewModelScope.launch {
            try {
                // 获取分组
                val group = groupList.value?.find { it.id == groupId }
                GlobalConfig.groups = GlobalConfig.groups.toMutableList().apply {
                    remove(groupId)
                }
                group?.let {
                    it.config.mmkv.clearAll()
                    if (deleteFiles) {
                        SnapshotApp.getInstance().fileSystem.delete(it.path)
                    }
                }
                // 通知UI更新
                loadGroups()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

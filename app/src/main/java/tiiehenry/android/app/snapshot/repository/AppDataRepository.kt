package tiiehenry.android.app.snapshot.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.config.GlobalConfig
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.utils.AppIconUtils
import tiiehenry.android.snapshot.app.IAppManager
import tiiehenry.android.snapshot.app.UserInfoHide
import tiiehenry.android.snapshot.file.IFileSystem
import java.nio.file.Paths
import java.util.UUID
import kotlin.io.path.absolutePathString

/**
 * 应用数据仓库 - 单例
 * 管理分组配置、应用列表、缓存状态等数据操作
 * 使用进程级协程作用域（SupervisorJob + Dispatchers.IO）
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

    val groupList = MutableLiveData<List<SnapGroup>>()
    val appsList = MutableLiveData<Map<UserInfoHide, List<AppInfo>>>(emptyMap())
    val isAppsLoading = MutableLiveData<Boolean>(false)

    fun loadData(context: Context, fileSystem: IFileSystem, appManager: IAppManager) {
        scope.launch {
            loadGroups(context, fileSystem, appManager)
            loadApps(fileSystem, appManager)
        }
    }

    suspend fun loadGroups(context: Context, fileSystem: IFileSystem, appManager: IAppManager) {
        Log.i(TAG, "loadGroups")
        val groupIds = GlobalConfig.groups
        val groups = withContext(Dispatchers.IO) {
            groupIds.map { groupId ->
                Log.i(TAG, "loadGroup: $groupId")
                SnapGroup(groupId).apply {
                    loadApps(context, fileSystem, appManager, true)
                }
            }
        }
        groupList.postValue(groups)
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
        name: String,
        path: String,
        userId: Int = 0,
        onComplete: (() -> Unit)? = null
    ) {
        scope.launch {
            val groupId = UUID.randomUUID().toString().substring(0, 7)
            val group = SnapGroup(groupId)
            group.path = path
            group.name = name
            group.config.groupConfigData.userId = userId
            group.config.save()

            val currentGroups = GlobalConfig.groups.toMutableList()
            currentGroups.add(groupId)
            GlobalConfig.groups = currentGroups

            onComplete?.invoke()
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
                val group = currentGroups.find { it.id == groupId } ?: return@launch
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
                group.loadApps(context, fileSystem, appManager, true)
                Log.d("addAppsToGroup", "Loaded ${group.apps.size} apps")

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
        fileSystem: IFileSystem,
        groupId: String,
        currentGroups: List<SnapGroup>,
        deleteFiles: Boolean = false,
        onComplete: (() -> Unit)? = null
    ) {
        scope.launch {
            try {
                val group = currentGroups.find { it.id == groupId }
                GlobalConfig.groups = GlobalConfig.groups.toMutableList().apply {
                    remove(groupId)
                }
                group?.let {
                    it.config.mmkv.clearAll()
                    if (deleteFiles) {
                        fileSystem.delete(it.path)
                    }
                }
                onComplete?.invoke()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

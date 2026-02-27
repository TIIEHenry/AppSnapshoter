package tiiehenry.android.app.snapshotor

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshotor.app.AppInfo
import tiiehenry.android.app.snapshotor.config.GlobalConfig
import tiiehenry.android.app.snapshotor.group.SnapGroup
import tiiehenry.android.snapshotor.fs.IFileType
import java.io.ByteArrayOutputStream
import java.nio.file.Paths
import java.util.UUID
import kotlin.io.path.absolutePathString

class SnapShotViewModel : ViewModel() {
    companion object {
        const val TAG = "SnapShotViewModel"
    }

    val groupList = MutableLiveData<List<SnapGroup>>()

    /**
     * userId to appInfo list
     */
    val appsList = MutableLiveData<Map<Int, List<AppInfo>>>(emptyMap())

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.IO) {
                loadGroups()
                loadApps()
            }
        }
    }

    fun loadGroups() {
        Log.i(TAG, "loadGroups")
        val groupIds = GlobalConfig.groups
        val groups = groupIds.map { groupId ->
            Log.i(TAG, "loadGroup: $groupId")
            SnapGroup(groupId).apply {
                loadApps(
                    SnapShotApp.getInstance().fileSystem,
                    SnapShotApp.getInstance().appManager,
                    true
                )
            }
        }
        groupList.postValue(groups)
    }

    private fun loadApps() {
        Log.i(TAG, "loadApps")
        try {
            // 从系统加载已安装应用列表
            val packageNames =
                SnapShotApp.getInstance().appManager.getInstalledPackages(0, 0) ?: emptyList()
            val appsMap = mutableMapOf<Int, List<AppInfo>>()
            val apps = packageNames.mapNotNull { packageName ->
                try {
                    val packageInfo =
                        SnapShotApp.getInstance().appManager.getPackageInfo(packageName, 0, 0)
                    val appInfo = AppInfo(
                        fs = SnapShotApp.getInstance().fileSystem,
                        appManager = SnapShotApp.getInstance().appManager,
                        packageName = packageName,
                        userId = 0,
                        versionName = packageInfo?.versionName,
                        versionCode = packageInfo?.longVersionCode ?: 0
                    )
                    appInfo
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            appsMap[0] = apps
            appsList.postValue(appsMap)
        } catch (e: Exception) {
            e.printStackTrace()
            appsList.postValue(emptyMap())
        }
    }

    fun addGroup(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // 生成唯一ID
                val groupId = UUID.randomUUID().toString().substring(0, 7)

                SnapGroup(groupId).name = name

                // 保存到全局配置
                val currentGroups = GlobalConfig.groups.toMutableSet()
                currentGroups.add(groupId)
                GlobalConfig.groups = currentGroups

                // 重新加载分组列表
                loadGroups()
            }
        }
    }

    fun addAppsToGroup(groupId: String, appInfos: List<AppInfo>,callback:()-> Unit) {
        viewModelScope.launch {
            try {
                // 获取分组
                val group = groupList.value?.find { it.id == groupId } ?: return@launch
                for (appInfo in appInfos) {
                    val packageName = appInfo.packageName
                    android.util.Log.d("addAppsToGroup", "Adding app: $packageName to group: ${group.id}")
                    // 创建应用目录
                    val packageDir = Paths.get(group.path, packageName).absolutePathString()
                    if (!SnapShotApp.getInstance().fileSystem.exists(packageDir)) {
                        SnapShotApp.getInstance().fileSystem.mkdirs(packageDir)
                    }

                    // 保存应用图标
                    val iconFile =
                        Paths.get(group.path, "$packageName.png").absolutePathString()
                    val icon = SnapShotApp.getInstance().appManager.loadIcon(packageName, 0)
                    if (icon != null) {
                        saveIconToFile(icon, iconFile)
                        android.util.Log.d("addAppsToGroup", "Saved icon to: $iconFile")
                    } else {
                        android.util.Log.w("addAppsToGroup", "Failed to load icon for $packageName")
                    }
                }
                // 重新加载分组的应用列表
                group.loadApps(
                    SnapShotApp.getInstance().fileSystem,
                    SnapShotApp.getInstance().appManager,
                    true
                )
                android.util.Log.d("addAppsToGroup", "Loaded ${group.apps.size} apps")

                // 通知 UI 更新
//                loadGroups()
                withContext(Dispatchers.Main){
                    callback()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("addAppsToGroup", "Error: ${e.message}", e)
            }
        }
    }

    private fun saveIconToFile(bitmap: Bitmap, filePath: String) {
        try {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            val byteArray = outputStream.toByteArray()

            SnapShotApp.getInstance().fileSystem.openFile(
                filePath,
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_WRITE_ONLY
            ).use { pfd ->
                ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { fos ->
                    fos.write(byteArray)
                    fos.flush()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            try {
                // 获取分组
                GlobalConfig.groups = GlobalConfig.groups.toMutableSet().apply {
                    remove(groupId)
                }
                groupList.value?.find { it.id == groupId }?.let {
                    SnapShotApp.getInstance().fileSystem.delete(it.path)
                }
                // 通知UI更新
                loadGroups()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

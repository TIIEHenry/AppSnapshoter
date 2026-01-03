package tiieherny.android.app.snapshotor

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiieherny.android.app.snapshotor.app.AppInfo
import tiieherny.android.app.snapshotor.config.GlobalConfig
import tiieherny.android.app.snapshotor.group.SnapGroup
import tiiehenry.android.shapshotor.fs.IFileType
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
                    // 创建应用目录
                    val packageDir = Paths.get(group.path, packageName).absolutePathString()
                    if (SnapShotApp.getInstance().fileSystem.fileType(packageDir) == IFileType.TYPE_NONE) {
                        SnapShotApp.getInstance().fileSystem.mkdirs(packageDir)
                    }

                    // 保存应用图标
                    val iconFile =
                        Paths.get(group.path, "$packageName.png").absolutePathString()
                    val icon = SnapShotApp.getInstance().appManager.loadIcon(packageName, 0)
                    if (icon != null) {
                        saveIconToFile(icon, iconFile)
                    }
                }
                // 重新加载分组的应用列表
                group.loadApps(
                    SnapShotApp.getInstance().fileSystem,
                    SnapShotApp.getInstance().appManager,
                    true
                )

                // 通知UI更新
//                loadGroups()
                callback()
            } catch (e: Exception) {
                e.printStackTrace()
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

package tiiehenry.android.app.snapshotor.main.launch

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshotor.SnapShotApp
import tiiehenry.android.app.snapshotor.app.AppInfo
import tiiehenry.android.app.snapshotor.archive.ArchiveItem
import tiiehenry.android.app.snapshotor.config.CompressItems
import tiiehenry.android.app.snapshotor.data.ArchivedApks
import tiiehenry.android.app.snapshotor.data.MetaDataItem
import tiiehenry.android.app.snapshotor.data.MetaInfoHelper
import tiiehenry.android.app.snapshotor.group.SnapedApp
import tiiehenry.android.app.snapshotor.ui.dialog.LoadingDialog
import tiiehenry.android.app.snapshotor.util.ApkUtil
import tiiehenry.android.snapshotor.app.IAppManager
import tiiehenry.android.snapshotor.file.ICompressCallback
import tiiehenry.android.snapshotor.file.IFileSystem
import tiiehenry.android.snapshotor.fs.CompressState
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.pathString

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    fun onGroupItemClicked(
        context: Context,
        groupId: String,
        mmkv: MMKV,
        packageName: String,
        item: SnapedApp
    ) {
        val snapShotApp = SnapShotApp.getInstance()
        val fs = snapShotApp.fileSystem
        val appManager = snapShotApp.appManager

        // 获取最新的存档
        val archiveItem = item.latestArchive
        if (archiveItem == null) {
            Toast.makeText(context, "没有可用的存档", Toast.LENGTH_SHORT).show()
            return
        }

        // 显示 LoadingDialog
        val loadingDialog = LoadingDialog(context)
        loadingDialog.setMessage("正在准备恢复存档...")
        loadingDialog.show()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                restoreArchive(context, fs, appManager, item, archiveItem, loadingDialog)
                val endTime = System.currentTimeMillis()
                Log.i("LauncherViewModel", "恢复存档耗时: ${endTime - startTime} ms")
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    Toast.makeText(context, "恢复失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun restoreArchive(
        context: Context,
        fs: IFileSystem,
        appManager: IAppManager,
        snapedApp: SnapedApp,
        archiveItem: ArchiveItem,
        loadingDialog: LoadingDialog
    ) {
        val appInfo = archiveItem.appInfo
        val packageName = appInfo.packageName
        val userId = appInfo.userId

        // 回调用于更新进度
        val callback = object : ICompressCallback.Stub() {
            override fun onStart() {
                loadingDialog.post {
                    loadingDialog.setMessage("开始恢复...")
                }
            }

            override fun onProgress(bytesWritten: Long, kbPerS: Long) {
                loadingDialog.post {
                    val progress = (bytesWritten / 1024).toInt()
                    loadingDialog.setProgress(progress)
                }
            }

            override fun onDone(originSize: Long, targetSize: Long, md5: String) {
            }

            override fun onError(msg: String?) {
                Log.e("LauncherViewModel", "Error: $msg")
                loadingDialog.post {
                    loadingDialog.setMessage("错误: $msg")
                }
            }
        }

        // 遍历数据项进行恢复
        val dataItems = archiveItem.dataItems
        var currentIndex = 0
        val totalItems = dataItems.size

        // 恢复前先清除应用数据
        withContext(Dispatchers.Main) {
            loadingDialog.setMessage("清除应用数据...")
        }
        val installed = appManager.isInstalled(packageName, userId)

        var needInstallApk = true
        if (installed) {
            // 检查已安装的apk，版本号和length一致就跳过
            val archiveVersionCode = archiveItem.metaInfo.packageInfo.versionCode
            val archiveApkSize = archiveItem.metaInfo.packageInfo.size

            // 获取已安装应用的 APK 信息
            val installedPackageInfo =
                appManager.getPackageInfo(packageName, PackageManager.GET_META_DATA, userId)
            if (installedPackageInfo != null) {
                val installedVersionCode = installedPackageInfo.longVersionCode
                // 计算已安装 APK 的总大小（包含 split APK）
                val installedApkInfo = installedPackageInfo.applicationInfo
                val installedApkSize = if (installedApkInfo != null) {
                    ApkUtil.calculateInstalledApkSize(fs, installedApkInfo)
                } else {
                    0L
                }

                // 比较版本号和大小
                if (installedVersionCode == archiveVersionCode && installedApkSize == archiveApkSize) {
                    needInstallApk = false
                }
            }
        }
        if (installed) {
            appManager.clearAppData(packageName, userId)
        }
        val toMutableList = dataItems.toMutableList()
        if (needInstallApk) {
            val apkDataItemDir = ArchivedApks.getArchivedApkDir(
                snapedApp.packageDir,
                archiveItem.metaInfo.packageInfo.versionCode
            )
            val apkDataItem = MetaInfoHelper.readDataItem(
                "" + archiveItem.metaInfo.packageInfo.size + MetaInfoHelper.DATA_ITEM_FILE_EXTENSION,
                apkDataItemDir
            )
            apkDataItem.let {
                it.name = CompressItems.COMPRESS_ITEM_APK
                toMutableList.remove(it)
                toMutableList.add(0, it)
            }
        } else {
            Log.i("LauncherViewModel", "skip install apk")
        }
        Log.i("LauncherViewModel", "toMutableList: $toMutableList")
        for (dataItem in toMutableList) {
            currentIndex++
            val progress = (currentIndex * 100) / (totalItems)
            val itemName = dataItem.name

            withContext(Dispatchers.Main) {
                loadingDialog.setCurrentItem("$itemName")
                loadingDialog.setProgress(progress)
            }

            val shouldContinue = restoreDataItem(
                fs,
                appManager,
                snapedApp,
                archiveItem,
                dataItem,
                appInfo,
                packageName,
                userId,
                callback,
                loadingDialog
            )
            if (!shouldContinue) {
                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                }
                return
            }
        }

        withContext(Dispatchers.Main) {
            loadingDialog.dismiss()
            Toast.makeText(context, "存档恢复成功", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun restoreDataItem(
        fs: IFileSystem,
        appManager: IAppManager,
        snapedApp: SnapedApp,
        archiveItem: ArchiveItem,
        dataItem: MetaDataItem,
        appInfo: AppInfo,
        packageName: String,
        userId: Int,
        callback: ICompressCallback,
        loadingDialog: LoadingDialog
    ): Boolean {
        val itemName = dataItem.name

        return when (itemName) {
            CompressItems.COMPRESS_ITEM_APK -> {
                // 恢复APK
                withContext(Dispatchers.Main) {
                    loadingDialog.setMessage("正在恢复APK...")
                }
                restoreApk(
                    fs,
                    appManager,
                    snapedApp,
                    archiveItem,
                    dataItem,
                    packageName,
                    userId,
                    callback
                )
            }

            CompressItems.COMPRESS_ITEM_DATA -> {
                // 恢复应用数据
                withContext(Dispatchers.Main) {
                    loadingDialog.setMessage("正在恢复应用数据...")
                }
                restoreData(fs, archiveItem, dataItem, appInfo.getDataDir(), callback)
                true
            }

            CompressItems.COMPRESS_ITEM_USER -> {
                // 恢复用户数据
                withContext(Dispatchers.Main) {
                    loadingDialog.setMessage("正在恢复用户数据...")
                }
                restoreData(fs, archiveItem, dataItem, appInfo.getUserDir(), callback)
                true
            }

            CompressItems.COMPRESS_ITEM_USER_DE -> {
                // 恢复用户DE数据
                withContext(Dispatchers.Main) {
                    loadingDialog.setMessage("正在恢复用户DE数据...")
                }
                restoreData(fs, archiveItem, dataItem, appInfo.getUserDeDir(), callback)
                true
            }

            CompressItems.COMPRESS_ITEM_OBB -> {
                // 恢复OBB数据
                withContext(Dispatchers.Main) {
                    loadingDialog.setMessage("正在恢复OBB数据...")
                }
                restoreData(fs, archiveItem, dataItem, appInfo.getObbDir(), callback)
                true
            }

            CompressItems.COMPRESS_ITEM_EXTERNAL_DATA -> {
                // 恢复外部数据
                withContext(Dispatchers.Main) {
                    loadingDialog.setMessage("正在恢复外部数据...")
                }
                restoreData(fs, archiveItem, dataItem, appInfo.getExternalDataDir(), callback)
                true
            }

            else -> {
                // 未知类型，跳过
                true
            }
        }
    }

    private fun restoreApk(
        fs: IFileSystem,
        appManager: IAppManager,
        snapedApp: SnapedApp,
        archiveItem: ArchiveItem,
        dataItem: MetaDataItem,
        packageName: String,
        userId: Int,
        callback: ICompressCallback
    ): Boolean {
        Log.i("LauncherViewModel", "restoreApk: $packageName")
        // 查找APK文件
        val archiveDir = ArchivedApks.getArchivedApkDir(
            snapedApp.packageDir,
            archiveItem.metaInfo.packageInfo.versionCode
        )
        val apkFile = Paths.get(archiveDir, dataItem.file).toString()

        if (!fs.exists(apkFile)) {
            callback.onError("APK文件不存在: $apkFile")
            return false
        }

        // 创建临时目录用于解压APK
        val tempDir = fs.createTempFile("apk-", ".tmp")
        fs.delete(tempDir)
        fs.mkdirs(tempDir)

        try {
            // 解压APK
            val decompressor = fs.compressor
            val algorithm = dataItem.algorithm.ifEmpty { decompressor.detectAlgorithm(apkFile) }
            val task = decompressor.decompress(algorithm, apkFile, tempDir, callback)
            task.start()

            // 等待解压完成
            var state = task.state()
            while (state == CompressState.COMPRESS_STATE_RUNNING || state == CompressState.COMPRESS_STATE_NONE) {
                Thread.sleep(100)
                state = task.state()
            }

            if (state != CompressState.COMPRESS_STATE_COMPLETE) {
                callback.onError("APK解压失败")
                return false
            }

            // 查找解压后的APK文件并安装
            val apkFiles = mutableListOf<String>()
            listApkFiles(fs, tempDir, apkFiles)

            if (apkFiles.isEmpty()) {
                callback.onError("未找到APK文件")
                return false
            }

            if (apkFiles.size == 1) {
                val startTime = System.currentTimeMillis()
                val installResult = appManager.installApk(apkFiles.first(), userId)
                val endTime = System.currentTimeMillis()
                if (!installResult) {
                    callback.onError("APK安装失败: ${apkFiles.first()}")
                    return false
                } else {
                    Log.i("LauncherViewModel", "APK安装耗时: ${endTime - startTime} ms")
                }
            } else {
                // 多个 split apk 使用 pm install-create/write/commit 会话安装
                val installResult = appManager.installApks(apkFiles, userId)
                if (!installResult) {
                    callback.onError("APK安装失败: ${apkFiles.joinToString(", ")}")
                    return false
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            callback.onError("恢复APK出错: ${e.message}")
            return false
        } finally {
            // 清理临时目录
            deleteDirectoryRecursively(fs, tempDir)
        }
    }

    private fun deleteDirectoryRecursively(fs: IFileSystem, path: String) {
        try {
            val fileType = fs.fileType(path)
            if (fileType == 1) { // TYPE_DIR
                val files = fs.listDir(path)
                for (file in files) {
                    deleteDirectoryRecursively(fs, "$path/$file")
                }
            }
            fs.delete(path)
        } catch (e: Exception) {
            // 忽略删除错误
        }
    }

    private fun listApkFiles(fs: IFileSystem, dir: String, apkFiles: MutableList<String>) {
        val fileType = fs.fileType(dir)
        if (fileType == 1) { // TYPE_DIR
            val files = fs.listDir(dir)
            for (file in files) {
                val fullPath = "$dir/$file"
                if (fullPath.endsWith(".apk")) {
                    apkFiles.add(fullPath)
                } else if (fs.fileType(fullPath) == 1) { // TYPE_DIR
                    listApkFiles(fs, fullPath, apkFiles)
                }
            }
        }
    }

    private fun restoreData(
        fs: IFileSystem,
        archiveItem: ArchiveItem,
        dataItem: MetaDataItem,
        targetDir: String,
        callback: ICompressCallback
    ): Boolean {
        // 查找数据文件
        val archiveDir = archiveItem.path
        val dataFile = Paths.get(archiveDir, dataItem.file).toString()

        if (!fs.exists(dataFile)) {
            callback.onError("数据文件不存在: $dataFile")
            return true // 返回 true 以继续处理其他数据项
        }

        try {
            // 确保目标目录存在
            val parentDir = fs.getParent(targetDir) ?: ""
            if (parentDir.isNotEmpty()) {
                fs.mkdirs(parentDir)
            }

            // 解压数据
            val decompressor = fs.compressor
            val algorithm = dataItem.algorithm.ifEmpty { decompressor.detectAlgorithm(dataFile) }
            val task = decompressor.decompress(algorithm, dataFile, targetDir, callback)
            task?.start()

            // 等待解压完成
            var state = task?.state() ?: CompressState.COMPRESS_STATE_ERROR
            while (state == CompressState.COMPRESS_STATE_RUNNING || state == CompressState.COMPRESS_STATE_NONE) {
                Thread.sleep(100)
                state = task?.state() ?: CompressState.COMPRESS_STATE_ERROR
            }

            if (state != CompressState.COMPRESS_STATE_COMPLETE) {
                callback.onError("数据解压失败: ${dataItem.file}")
            }

            return true
        } catch (e: Exception) {
            callback.onError("恢复数据出错: ${e.message}")
            return true
        }
    }
}
package tiiehenry.android.app.snapshot.main.launch

import android.app.AppOpsManagerHidden
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.RemoteException
import android.text.format.Formatter
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONWriter
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.config.CompressItems
import tiiehenry.android.app.snapshot.data.ArchivedApks
import tiiehenry.android.app.snapshot.data.MetaDataItem
import tiiehenry.android.app.snapshot.data.MetaInfoHelper
import tiiehenry.android.app.snapshot.group.SnapedApp
import tiiehenry.android.app.snapshot.ui.dialog.ILoadingDialog
import tiiehenry.android.app.snapshot.ui.dialog.LoadingDialog
import tiiehenry.android.app.snapshot.util.ApkUtil
import tiiehenry.android.snapshot.app.AppPermission
import tiiehenry.android.snapshot.app.IAppManager
import tiiehenry.android.snapshot.file.ICompressCallback
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.fs.CompressState
import tiiehenry.android.snapshot.provider.appmanager.root.SELinux
import java.io.File
import java.nio.file.Paths

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * 高级恢复：只恢复选中的数据类型
     */
    fun onAdvancedRestoreClicked(
        context: Context,
        snapedApp: SnapedApp,
        archiveItem: ArchiveItem,
        selectedTypes: Set<String>,
        updateCurrent: () -> Unit
    ) {
        val snapShotApp = SnapshotApp.getInstance()
        val fs = snapShotApp.fileSystem
        val appManager = snapShotApp.appManager

        // 显示 LoadingDialog
        val loadingDialog = LoadingDialog(context)
        loadingDialog.setItemMessage("正在准备高级恢复")
        loadingDialog.setItemStatus("...")
        loadingDialog.showItem()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                restoreArchiveWithSelectedTypes(
                    context,
                    fs,
                    appManager,
                    snapedApp,
                    archiveItem,
                    loadingDialog,
                    selectedTypes
                )
                val endTime = System.currentTimeMillis()
                Log.i("LauncherViewModel", "高级恢复存档耗时: ${endTime - startTime} ms")
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    loadingDialog.dismissItem()
                    Toast.makeText(context, "恢复失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                updateCurrent()
            }
        }
    }

    fun onGroupItemClicked(
        context: Context,
        groupId: String,
        mmkv: MMKV,
        packageName: String,
        item: SnapedApp,
        updateCurrent: () -> Unit
    ) {
        val snapShotApp = SnapshotApp.getInstance()
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
        loadingDialog.setItemMessage("正在准备恢复存档")
        loadingDialog.setItemStatus("...")
        loadingDialog.showItem()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                restoreArchive(context, fs, appManager, item, archiveItem, loadingDialog)
                val endTime = System.currentTimeMillis()
                Log.i("LauncherViewModel", "恢复存档耗时: ${endTime - startTime} ms")
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    loadingDialog.dismissItem()
                    Toast.makeText(context, "恢复失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    updateCurrent()
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
        loadingDialog: ILoadingDialog
    ) {
        val appInfo = archiveItem.appInfo
        val packageName = appInfo.packageName
        val userId = appInfo.userId

        // 回调用于更新进度
        val callback = itemCallback(loadingDialog, context)

        // 遍历数据项进行恢复
        val dataItems = archiveItem.dataItems

        // 恢复前先清除应用数据
        withContext(Dispatchers.Main) {
            loadingDialog.setItemMessage("清除应用数据")
            loadingDialog.setItemStatus("...")
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
        val needRestoreDataItems = dataItems.toMutableList()
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
                needRestoreDataItems.remove(it)
                needRestoreDataItems.add(0, it)
            }
        } else {
            Log.i("LauncherViewModel", "skip install apk")
        }
        Log.i("LauncherViewModel", "toMutableList: $needRestoreDataItems")
        val extraItems = archiveItem.extraItems

        if (!restoreItems(
                needRestoreDataItems,
                extraItems,
                loadingDialog,
                fs,
                appManager,
                snapedApp,
                archiveItem,
                appInfo,
                packageName,
                userId,
                callback
            )
        ) return

        restorePermission(loadingDialog, archiveItem, fs, appManager, packageName, userId)
        withContext(Dispatchers.Main) {
            loadingDialog.dismissItem()
            Toast.makeText(context, "存档恢复成功", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun restoreItems(
        needRestoreDataItems: MutableList<MetaDataItem>,
        extraItems: Map<MetaDataItem, String>,
        loadingDialog: ILoadingDialog,
        fs: IFileSystem,
        appManager: IAppManager,
        snapedApp: SnapedApp,
        archiveItem: ArchiveItem,
        appInfo: AppInfo,
        packageName: String,
        userId: Int,
        callback: DataItemCallback
    ): Boolean {
        var currentIndex = 0
        val totalItems = needRestoreDataItems.size + extraItems.size

        try {
            // 恢复标准数据项
            for (dataItem in needRestoreDataItems) {
                currentIndex++
                val progress = (currentIndex * 100) / totalItems
                val itemName = dataItem.name

                withContext(Dispatchers.Main) {
                    loadingDialog.setCurrentItem("$itemName")
                    loadingDialog.setItemProgress(progress)
                }

                restoreDataItem(
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
            }

            // 恢复额外项目
            if (extraItems.isNotEmpty()) {
                Log.i("LauncherViewModel", "开始恢复 ${extraItems.size} 个额外项目")
                for ((extraDataItem, extraPath) in extraItems) {
                    currentIndex++
                    val progress = (currentIndex * 100) / totalItems
                    val itemName = extraDataItem.name

                    withContext(Dispatchers.Main) {
                        loadingDialog.setCurrentItem("$itemName (额外)")
                        loadingDialog.setItemProgress(progress)
                    }
                    restoreExtraItem(
                        fs,
                        appManager,
                        archiveItem,
                        extraDataItem,
                        extraPath,
                        callback,
                        loadingDialog
                    )
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                loadingDialog.setItemException(e)
            }
            return false
        }
        return true
    }

    private suspend fun restorePermission(
        loadingDialog: ILoadingDialog,
        archiveItem: ArchiveItem,
        fs: IFileSystem,
        appManager: IAppManager,
        packageName: String,
        userId: Int
    ) {
        // 读取不可变更的权限列表
        val fixedPermissionsFile =
            File(SnapshotApp.getInstance().globalRootPath, "fixed_permissions.json")
        val fixedPermissions = if (fixedPermissionsFile.exists()) {
            try {
                val jsonStr = fixedPermissionsFile.readText()
                JSON.parseArray(jsonStr, String::class.java) ?: emptyList<String>()
            } catch (e: Exception) {
                Log.e("LauncherViewModel", "读取 fixed_permissions.json 失败：${e.message}")
                emptyList<String>()
            }
        } else {
            emptyList<String>()
        }.toMutableList()
        Log.i("LauncherViewModel", "已加载 ${fixedPermissions.size} 个不可变更的权限")

        // 恢复权限
        withContext(Dispatchers.Main) {
            loadingDialog.setItemMessage("正在恢复权限")
            loadingDialog.setItemStatus("...")
        }
        val permissionsFile = "${archiveItem.path}/${MetaInfoHelper.PERMISSIONS_FILE_NAME}"
        val metaPermissions = MetaInfoHelper.readPermissions(fs, permissionsFile)
        val newFixedPermissions = mutableListOf<String>()
        if (metaPermissions.isNotEmpty()) {
            val appPermissions = metaPermissions.map { metaPermission ->
                AppPermission(
                    metaPermission.isGranted(),
                    metaPermission.mode,
                    metaPermission.name,
                    metaPermission.op
                )
            }

            // 获取 uid 和 UserHandle
            val uid = appManager.getPackageUid(packageName, userId)
            val user = appManager.getUserHandle(userId)

            if (uid != -1 && user != null) {
                // 重置 AppOps
                appManager.resetAppOps(userId, packageName)

                Log.i("LauncherViewModel", "Permissions size: ${appPermissions.size}...")

                // 用于记录新增的不可变更权限

                appPermissions.forEach {
                    Log.i(
                        "LauncherViewModel",
                        "Permission name: ${it.name}, isGranted: ${it.isGranted}, op: ${it.op}, mode: ${it.mode}"
                    )

                    // 检查是否在不可变更的权限列表中
                    val isFixed = fixedPermissions.contains(it.name)
                    if (isFixed) {
                        Log.i("LauncherViewModel", "跳过不可变更的权限：${it.name}")
                        return@forEach
                    }

                    runCatching {
                        try {
                            if (it.isGranted) {
                                appManager.grantRuntimePermission(packageName, it.name, user)
                            } else {
                                appManager.revokeRuntimePermission(packageName, it.name, user)
                            }
                        } catch (e: RemoteException) {
                            if (e.message?.contains("not a changeable permission type") == true) {
                                // 记录不可变更的权限名称
                                Log.w(
                                    "LauncherViewModel",
                                    "权限 ${it.name} 是不可变更的类型，添加到固定列表"
                                )
                                fixedPermissions.add(it.name)
                                newFixedPermissions.add(it.name)
                            } else {
                                e.printStackTrace()
                            }
                        }
                        if (it.op != AppOpsManagerHidden.OP_NONE) {
                            appManager.setOpsMode(it.op, uid, packageName, it.mode)
                        }
                    }
                }
                Log.i("LauncherViewModel", "已恢复 ${appPermissions.size} 个权限")
            } else {
                Log.i("LauncherViewModel", "Failed to get uid or user handle for $packageName")
            }
        } else {
            Log.i("LauncherViewModel", "未找到权限数据或权限列表为空")
        }

        if (archiveItem.metaInfo.ssaid.isNotEmpty()) {
            appManager.setPackageSsaidAsUser(packageName, userId, archiveItem.metaInfo.ssaid)
        }


        // 如果有新增的不可变更权限，合并并保存
        if (newFixedPermissions.isNotEmpty()) {
            try {
                val str = JSON.toJSONString(fixedPermissions, JSONWriter.Feature.PrettyFormat)
                fixedPermissionsFile.writeText(str)
                Log.i("LauncherViewModel", "已保存 ${fixedPermissions.size} 个不可变更的权限")
            } catch (e: Exception) {
                Log.e("LauncherViewModel", "保存 fixed_permissions.json 失败：${e.message}")
            }
        }
    }

    private fun itemCallback(
        loadingDialog: ILoadingDialog,
        context: Context
    ): DataItemCallback {
        val callback = object : DataItemCallback {
            override fun onStart(dataItem: MetaDataItem) {
                loadingDialog.post {
                    loadingDialog.setItemStatus("...")
                }
            }

            override fun onProgress(dataItem: MetaDataItem, bytesWritten: Long, bytesPerS: Long) {
                loadingDialog.post {
                    val fileSize = Formatter.formatFileSize(context, bytesWritten)
                    loadingDialog.setItemMessage("已读取: $fileSize")
                    if (bytesPerS == 0L) {
                        loadingDialog.setItemStatus("...")
                    } else {
                        val speed = Formatter.formatFileSize(context, bytesPerS)
                        loadingDialog.setItemStatus("$speed/s")
                    }
                }
            }

            override fun onDone(
                dataItem: MetaDataItem,
                originSize: Long,
                targetSize: Long,
                md5: String
            ) {

            }

            override fun onError(dataItem: MetaDataItem, e: Exception) {
                loadingDialog.post {
                    loadingDialog.setItemException(e)
                }
            }
        }
        return callback
    }

    /**
     * 高级恢复：只恢复选中的数据类型
     */
    private suspend fun restoreArchiveWithSelectedTypes(
        context: Context,
        fs: IFileSystem,
        appManager: IAppManager,
        snapedApp: SnapedApp,
        archiveItem: ArchiveItem,
        loadingDialog: ILoadingDialog,
        selectedTypes: Set<String>
    ) {
        val appInfo = archiveItem.appInfo
        val packageName = appInfo.packageName
        val userId = appInfo.userId

        // 回调用于更新进度
        val callback = itemCallback(loadingDialog, context)

        // 过滤出选中的数据项
        val allDataItems = archiveItem.dataItems.toMutableList()
        val dataItems = allDataItems.filter { selectedTypes.contains(it.name) }

        // 处理 extraItems：根据用户选择过滤
        val selectedExtraItems =
            archiveItem.extraItems.filterKeys { selectedTypes.contains(it.name) }
        Log.i("LauncherViewModel", "高级恢复：选中 ${selectedExtraItems.size} 个额外项目")

        if (dataItems.isEmpty() && selectedExtraItems.isEmpty()) {
            withContext(Dispatchers.Main) {
                loadingDialog.dismissItem()
                Toast.makeText(context, "没有选中的数据项", Toast.LENGTH_SHORT).show()
            }
            return
        }


        // 检查是否需要恢复APK
        val needRestoreApk = selectedTypes.contains(CompressItems.COMPRESS_ITEM_APK)
        val installed = appManager.isInstalled(packageName, userId)

        // 如果需要恢复APK，检查是否需要重新安装
        var needInstallApk = needRestoreApk
        if (needRestoreApk && installed) {
            val archiveVersionCode = archiveItem.metaInfo.packageInfo.versionCode
            val archiveApkSize = archiveItem.metaInfo.packageInfo.size

            val installedPackageInfo =
                appManager.getPackageInfo(packageName, PackageManager.GET_META_DATA, userId)
            if (installedPackageInfo != null) {
                val installedVersionCode = installedPackageInfo.longVersionCode
                val installedApkInfo = installedPackageInfo.applicationInfo
                val installedApkSize = if (installedApkInfo != null) {
                    ApkUtil.calculateInstalledApkSize(fs, installedApkInfo)
                } else {
                    0L
                }

                if (installedVersionCode == archiveVersionCode && installedApkSize == archiveApkSize) {
                    needInstallApk = false
                    Log.i("LauncherViewModel", "高级恢复: 跳过APK安装，版本相同")
                }
            }
        }

        // 如果恢复数据但不恢复APK，且应用未安装，则报错
        val hasDataTypes = selectedTypes.any { it != CompressItems.COMPRESS_ITEM_APK }
        if (hasDataTypes && !installed && !needInstallApk) {
            withContext(Dispatchers.Main) {
                loadingDialog.dismissItem()
                Toast.makeText(context, "应用未安装，请先恢复APK或安装应用", Toast.LENGTH_LONG)
                    .show()
            }
            return
        }

        // 如果恢复数据且应用已安装，清除应用数据
        if (hasDataTypes && installed) {
            withContext(Dispatchers.Main) {
                loadingDialog.setItemMessage("清除应用数据")
                loadingDialog.setItemStatus("...")
            }
            appManager.clearAppData(packageName, userId)
        }

        // 构建最终要恢复的数据项列表
        val needRestoreDataItems = dataItems.toMutableList()
        val apkDataItem = needRestoreDataItems.find { it.name == CompressItems.COMPRESS_ITEM_APK }

        if (apkDataItem != null) {
            if (!needInstallApk) {
                // 不需要安装APK，从列表中移除
                needRestoreDataItems.remove(apkDataItem)
            } else {
                // 需要安装APK，移到列表开头
                needRestoreDataItems.remove(apkDataItem)
                // 获取APK数据项的实际文件信息
                val apkDataItemDir = ArchivedApks.getArchivedApkDir(
                    snapedApp.packageDir,
                    archiveItem.metaInfo.packageInfo.versionCode
                )
                val realApkDataItem = MetaInfoHelper.readDataItem(
                    "" + archiveItem.metaInfo.packageInfo.size + MetaInfoHelper.DATA_ITEM_FILE_EXTENSION,
                    apkDataItemDir
                )
                realApkDataItem.name = CompressItems.COMPRESS_ITEM_APK
                needRestoreDataItems.add(0, realApkDataItem)
            }
        }

        if (!restoreItems(
                needRestoreDataItems,
                selectedExtraItems,
                loadingDialog,
                fs,
                appManager,
                snapedApp,
                archiveItem,
                appInfo,
                packageName,
                userId,
                callback
            )
        ) return

        // 恢复权限（仅当恢复了APK或数据时）
        if (needInstallApk || hasDataTypes) {
            restorePermission(loadingDialog, archiveItem, fs, appManager, packageName, userId)
        }

        withContext(Dispatchers.Main) {
            loadingDialog.dismissItem()
            Toast.makeText(context, "高级恢复成功", Toast.LENGTH_SHORT).show()
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
        callback: DataItemCallback,
        loadingDialog: ILoadingDialog
    ) {
        val itemName = dataItem.name

        when (itemName) {
            CompressItems.COMPRESS_ITEM_APK -> {
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
                restoreData(
                    fs,
                    appManager,
                    archiveItem,
                    dataItem,
                    appInfo.getPackageDataDir(),
                    callback
                )
            }

            CompressItems.COMPRESS_ITEM_USER -> {
                restoreData(fs, appManager, archiveItem, dataItem, appInfo.getUserDir(), callback)
            }

            CompressItems.COMPRESS_ITEM_USER_DE -> {
                restoreData(fs, appManager, archiveItem, dataItem, appInfo.getUserDeDir(), callback)
            }

            CompressItems.COMPRESS_ITEM_OBB -> {
                restoreData(
                    fs,
                    appManager,
                    archiveItem,
                    dataItem,
                    appInfo.getPackageObbDir(),
                    callback
                )
            }

            CompressItems.COMPRESS_ITEM_MEDIA -> {
                restoreData(
                    fs,
                    appManager,
                    archiveItem,
                    dataItem,
                    appInfo.getPackageMediaDir(),
                    callback
                )
            }

            else -> {
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
        callback: DataItemCallback
    ): Boolean {
        Log.i("LauncherViewModel", "restoreApk: $packageName")
        // 查找APK文件
        val archiveDir = ArchivedApks.getArchivedApkDir(
            snapedApp.packageDir,
            archiveItem.metaInfo.packageInfo.versionCode
        )
        val apkFile = Paths.get(archiveDir, dataItem.file).toString()

        if (!fs.exists(apkFile)) {
            throw MissingDataFileException(dataItem, apkFile)
        }

        // 创建临时目录用于解压APK
        val tempDir = fs.createTempFile("apk-", ".tmp")
        fs.delete(tempDir)
        fs.mkdirs(tempDir)

        var errorMsg: String? = null
        // 回调用于更新进度
        val callback = object : ICompressCallback.Stub() {
            override fun onStart() {
                callback.onStart(dataItem)
            }

            override fun onProgress(bytesWritten: Long, bytesPerS: Long) {
                callback.onProgress(dataItem, bytesWritten, bytesPerS)
            }

            override fun onDone(originSize: Long, targetSize: Long, md5: String) {
                callback.onDone(dataItem, originSize, targetSize, md5)
            }

            override fun onError(msg: String?) {
                errorMsg = msg
                callback.onError(dataItem, RestoreFailedException(dataItem, errorMsg))
            }
        }
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
                throw RestoreFailedException(dataItem, errorMsg)
            }

            // 查找解压后的APK文件并安装
            val apkFiles = mutableListOf<String>()
            listApkFiles(fs, tempDir, apkFiles)

            if (apkFiles.isEmpty()) {
                throw MissingDataFileException(dataItem, tempDir)
            }

            if (apkFiles.size == 1) {
                val startTime = System.currentTimeMillis()
                val installResult = appManager.installApk(apkFiles.first(), userId)
                val endTime = System.currentTimeMillis()
                if (!installResult) {
                    throw InstallFailedException(dataItem, apkFiles.first())
                } else {
                    Log.i("LauncherViewModel", "APK安装耗时: ${endTime - startTime} ms")
                }
            } else {
                // 多个 split apk 使用 pm install-create/write/commit 会话安装
                val installResult = appManager.installApks(apkFiles, userId)
                if (!installResult) {
                    throw InstallFailedException(dataItem, apkFiles.joinToString(", "))
                }
            }
            return true
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

    /**
     * 恢复额外项目
     */
    private suspend fun restoreExtraItem(
        fs: IFileSystem,
        appManager: IAppManager,
        archiveItem: ArchiveItem,
        dataItem: MetaDataItem,
        targetPath: String,
        callback: DataItemCallback,
        loadingDialog: ILoadingDialog
    ) {
        // 查找数据文件
        val archiveDir = archiveItem.path
        val dataFile = Paths.get(archiveDir, dataItem.file).toString()

        if (!fs.exists(dataFile)) {
            throw MissingDataFileException(dataItem, dataFile)
        }

        // Get the SELinux context of the path.
        val pathContext: String
        SELinux.getContext(path = targetPath).also { result ->
            pathContext = if (result.isSuccess) result.outString else ""
        }

        Log.i("LauncherViewModel", "额外项目原始 SELinux context: $pathContext.")

        // 确保目标目录的父目录存在
        val parentDir = fs.getParent(targetPath) ?: ""
        if (parentDir.isNotEmpty()) {
            fs.mkdirs(parentDir)
        }

        var errorMsg: String? = null
        // 回调用于更新进度
        val callback = object : ICompressCallback.Stub() {
            override fun onStart() {
                callback.onStart(dataItem)
            }

            override fun onProgress(bytesWritten: Long, bytesPerS: Long) {
                callback.onProgress(dataItem, bytesWritten, bytesPerS)
            }

            override fun onDone(originSize: Long, targetSize: Long, md5: String) {
                callback.onDone(dataItem, originSize, targetSize, md5)
            }

            override fun onError(msg: String?) {
                errorMsg = msg
                callback.onError(dataItem, RestoreFailedException(dataItem, errorMsg))
            }
        }
        // 解压数据
        val decompressor = fs.compressor
        val algorithm = dataItem.algorithm.ifEmpty { decompressor.detectAlgorithm(dataFile) }
        val task = decompressor.decompress(algorithm, dataFile, targetPath, callback)
        task.start()

        // 等待解压完成
        var state = task.state()
        while (state == CompressState.COMPRESS_STATE_RUNNING || state == CompressState.COMPRESS_STATE_NONE) {
            Thread.sleep(100)
            state = task.state()
        }
        var isSuccess = state == CompressState.COMPRESS_STATE_COMPLETE

        if (!isSuccess) {
            throw RestoreFailedException(dataItem, errorMsg)
        } else {
            // Restore SELinux context.
            val out = mutableListOf<String>()
            SELinux.chcon(context = pathContext, path = targetPath).also { result ->
                isSuccess = isSuccess && result.isSuccess
                out.addAll(result.out)
            }
            Log.i(
                "LauncherViewModel",
                "恢复额外项目 SELinux context: ${out.joinToString(", ")}"
            )
        }
    }

    private suspend fun restoreData(
        fs: IFileSystem,
        appManager: IAppManager,
        archiveItem: ArchiveItem,
        dataItem: MetaDataItem,
        targetDir: String,
        callback: DataItemCallback
    ) {
        // 查找数据文件
        val archiveDir = archiveItem.path
        val dataFile = Paths.get(archiveDir, dataItem.file).toString()

        if (!fs.exists(dataFile)) {
            throw MissingDataFileException(dataItem, dataFile)
        }

        val packageInfo = appManager.getPackageInfo(
            archiveItem.metaInfo.packageInfo.packageName,
            0,
            archiveItem.metaInfo.userId
        )
        val uid = packageInfo?.applicationInfo?.uid ?: throw MissingUidException(
            dataItem,
            archiveItem.metaInfo.packageInfo.packageName
        )

        // Get the SELinux context of the path.
        val pathContext: String
        SELinux.getContext(path = targetDir).also { result ->
            pathContext = if (result.isSuccess) result.outString else ""
        }

        Log.i("LauncherViewModel", "Original SELinux context: $pathContext.")
        // 确保目标目录存在
        val parentDir = fs.getParent(targetDir) ?: ""
        if (parentDir.isNotEmpty()) {
            fs.mkdirs(parentDir)
        }
        if (targetDir.endsWith("/" + archiveItem.metaInfo.userId)) {
            throw IllegalAccessError("！！！！！！！！！！！！ wrong dir: $targetDir")
        }
        var errorMsg: String? = null
        // 回调用于更新进度
        val callback = object : ICompressCallback.Stub() {
            override fun onStart() {
                callback.onStart(dataItem)
            }

            override fun onProgress(bytesWritten: Long, bytesPerS: Long) {
                callback.onProgress(dataItem, bytesWritten, bytesPerS)
            }

            override fun onDone(originSize: Long, targetSize: Long, md5: String) {
                callback.onDone(dataItem, originSize, targetSize, md5)
            }

            override fun onError(msg: String?) {
                errorMsg = msg
                callback.onError(dataItem, RestoreFailedException(dataItem, errorMsg))
            }
        }
        // 解压数据
        val decompressor = fs.compressor
        val algorithm = dataItem.algorithm.ifEmpty { decompressor.detectAlgorithm(dataFile) }
        val task = decompressor.decompress(algorithm, dataFile, targetDir, callback)
        task.start()

        // 等待解压完成
        var state = task.state()
        while (state == CompressState.COMPRESS_STATE_RUNNING || state == CompressState.COMPRESS_STATE_NONE) {
            Thread.sleep(100)
            state = task.state()
        }
        var isSuccess = state == CompressState.COMPRESS_STATE_COMPLETE

        if (!isSuccess) {
            throw RestoreFailedException(dataItem, errorMsg)
        } else {
            // Restore SELinux context.
            var gid: UInt = uid.toUInt()
            if (dataItem.name == CompressItems.COMPRESS_ITEM_DATA || dataItem.name == CompressItems.COMPRESS_ITEM_OBB || dataItem.name == CompressItems.COMPRESS_ITEM_MEDIA) {
                val pathGid = fs.getGid(targetDir)
                gid = pathGid.toUInt()
            }
            val out = mutableListOf<String>()
            SELinux.chown(uid = uid.toUInt(), gid = gid, path = targetDir).also { result ->
                isSuccess = isSuccess && result.isSuccess
                out.addAll(result.out)
            }
            if (pathContext.isNotEmpty()) {
                SELinux.chcon(context = pathContext, path = targetDir).also { result ->
                    isSuccess = isSuccess && result.isSuccess
                    out.addAll(result.out)
                }
            } else {
                //data/user/0/pkg的父文件夹就是0了，这里不要随便改
//                    val parentContext: String
//                    SELinux.getContext(parentDir).also { result ->
//                        parentContext = if (result.isSuccess) result.outString.replace(
//                            "system_data_file",
//                            "app_data_file"
//                        ) else ""
//                    }
//                    if (parentContext.isNotEmpty()) {
//                        SELinux.chcon(context = parentContext, path = targetDir).also { result ->
//                            isSuccess = isSuccess && result.isSuccess
//                            out.addAll(result.out)
//                        }
//                    } else {
//                        isSuccess = false
//                        out.add("Failed to restore context: $targetDir")
//                    }
            }
            Log.i("LauncherViewModel", "Restore SELinux context: ${out.joinToString(", ")}")
        }
    }
}
package tiiehenry.android.app.snapshot.archive.restore

import android.content.Context
import android.content.pm.PackageManager
import android.text.format.Formatter
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.archive.ArchivedApks
import tiiehenry.android.app.snapshot.archive.MetaInfoHelper
import tiiehenry.android.app.snapshot.archive.bean.MetaDataItem
import tiiehenry.android.app.snapshot.config.CompressItems
import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.app.snapshot.main.launch.makearchive.progress.IItemProgressDialog
import tiiehenry.android.app.snapshot.main.launch.makearchive.progress.ItemProgressDialog
import tiiehenry.android.app.snapshot.main.launch.batch.RestoreRecordWriter
import tiiehenry.android.app.snapshot.utils.ApksUtil
import tiiehenry.android.snapshot.app.IAppManager
import tiiehenry.android.snapshot.file.IFileSystem

/**
 * 存档恢复器 - 恢复流程编排入口
 * 内部职责已拆分为：
 * - [ApkInstaller] APK 安装
 * - [DataRestorer] 数据目录恢复
 * - [PermissionRestorer] 权限/AppOps/SSAID 恢复
 */
object ArchiveRestorer {

    private const val TAG = "ArchiveRestorer"

    private fun validateRestoreUserId(archiveItem: ArchiveItem) {
        val metaUserId = archiveItem.metaInfo.userId
        val targetUserId = archiveItem.appInfo.userId
        if (metaUserId != targetUserId) {
            throw IllegalStateException(
                SnapshotApp.getInstance().getString(
                    R.string.archive_user_mismatch,
                    metaUserId,
                    targetUserId
                )
            )
        }
    }

    suspend fun restoreArchiveSuspend(
        context: Context,
        archivedApp: ArchivedApp,
        archiveItem: ArchiveItem
    ) {
        val snapShotApp = SnapshotApp.getInstance()
        val fs = snapShotApp.fileSystem
        val appManager = snapShotApp.appManager
        val errorRef = AtomicReference<Exception?>(null)
        val noOpDialog = NoOpItemProgressDialog(errorRef)

        restoreArchive(context, fs, appManager, archivedApp, archiveItem, noOpDialog)

        errorRef.get()?.let { throw it }
    }

    private class NoOpItemProgressDialog(
        private val errorRef: AtomicReference<Exception?>
    ) : IItemProgressDialog {
        override fun setItemMessage(message: String) {}
        override fun setItemStatus(message: String) {}
        override fun setItemProgress(progress: Int) {}
        override fun setCurrentItem(item: String) {}
        override fun showItem() {}
        override fun post(runnable: Runnable) { runnable.run() }
        override fun dismissItem() {}
        override fun setItemException(e: Exception) { errorRef.compareAndSet(null, e) }
    }

    fun restoreArchiveItem(
        context: Context,
        archivedApp: ArchivedApp,
        archiveItem: ArchiveItem,
        updateCurrent: () -> Unit,
        scope: CoroutineScope
    ) {
        val snapShotApp = SnapshotApp.getInstance()
        val fs = snapShotApp.fileSystem
        val appManager = snapShotApp.appManager

        val loadingDialog = ItemProgressDialog(context)
        loadingDialog.setItemMessage(context.getString(R.string.archive_preparing_restore))
        loadingDialog.setItemStatus(context.getString(R.string.ellipsis))
        loadingDialog.showItem()

        scope.launch(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                restoreArchive(context, fs, appManager, archivedApp, archiveItem, loadingDialog)
                val endTime = System.currentTimeMillis()
                Log.i(TAG, "恢复存档耗时: ${endTime - startTime} ms")
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    loadingDialog.dismissItem()
                    Toast.makeText(
                        context,
                        context.getString(R.string.archive_restore_failed, e.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    updateCurrent()
                }
            }
        }
    }

    fun restoreAdvanced(
        context: Context,
        archivedApp: ArchivedApp,
        archiveItem: ArchiveItem,
        selectedTypes: Set<String>,
        updateCurrent: () -> Unit,
        viewModelScope: CoroutineScope
    ) {
        val snapShotApp = SnapshotApp.getInstance()
        val fs = snapShotApp.fileSystem
        val appManager = snapShotApp.appManager

        val loadingDialog = ItemProgressDialog(context)
        loadingDialog.setItemMessage(context.getString(R.string.archive_preparing_advanced_restore))
        loadingDialog.setItemStatus(context.getString(R.string.ellipsis))
        loadingDialog.showItem()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                restoreArchiveWithSelectedTypes(
                    context,
                    fs,
                    appManager,
                    archivedApp,
                    archiveItem,
                    loadingDialog,
                    selectedTypes
                )
                val endTime = System.currentTimeMillis()
                Log.i(TAG, "高级恢复存档耗时: ${endTime - startTime} ms")
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    loadingDialog.dismissItem()
                    Toast.makeText(
                        context,
                        context.getString(R.string.archive_restore_failed, e.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                updateCurrent()
            }
        }
    }

    fun restoreLatest(
        archivedApp: ArchivedApp,
        context: Context,
        updateCurrent: () -> Unit,
        viewModelScope: CoroutineScope
    ) {
        val snapShotApp = SnapshotApp.getInstance()
        val fs = snapShotApp.fileSystem
        val appManager = snapShotApp.appManager

        val archiveItem = archivedApp.latestArchive
        if (archiveItem == null) {
            Toast.makeText(context, R.string.archive_no_available, Toast.LENGTH_SHORT).show()
            return
        }

        val loadingDialog = ItemProgressDialog(context)
        loadingDialog.setItemMessage(context.getString(R.string.archive_preparing_restore))
        loadingDialog.setItemStatus(context.getString(R.string.ellipsis))
        loadingDialog.showItem()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                restoreArchive(context, fs, appManager, archivedApp, archiveItem, loadingDialog)
                val endTime = System.currentTimeMillis()
                Log.i(TAG, "恢复存档耗时: ${endTime - startTime} ms")
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    loadingDialog.dismissItem()
                    Toast.makeText(
                        context,
                        context.getString(R.string.archive_restore_failed, e.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
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
        archivedApp: ArchivedApp,
        archiveItem: ArchiveItem,
        loadingDialog: IItemProgressDialog
    ) {
        validateRestoreUserId(archiveItem)

        val appInfo = archiveItem.appInfo
        val packageName = appInfo.packageName
        val userId = appInfo.userId

        val callback = itemCallback(loadingDialog, context)
        val dataItems = archiveItem.dataItems

        withContext(Dispatchers.Main) {
            loadingDialog.setItemMessage(context.getString(R.string.archive_clearing_app_data))
            loadingDialog.setItemStatus("...")
        }
        val installed = appManager.isInstalled(packageName, userId)

        var needInstallApk = true
        if (installed) {
            val archiveVersionCode = archiveItem.metaInfo.packageInfo.versionCode
            val archiveApkSize = archiveItem.metaInfo.packageInfo.size

            val installedPackageInfo =
                appManager.getPackageInfo(packageName, PackageManager.GET_META_DATA, userId)
            if (installedPackageInfo != null) {
                val installedVersionCode = installedPackageInfo.longVersionCode
                val installedApkInfo = installedPackageInfo.applicationInfo
                val installedApkSize = if (installedApkInfo != null) {
                    ApksUtil.calculateInstalledApkSize(fs, installedApkInfo)
                } else {
                    0L
                }

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
                archivedApp.packageDir,
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
            Log.i(TAG, "skip install apk")
        }
        Log.i(TAG, "toMutableList: $needRestoreDataItems")
        val extraItems = archiveItem.extraItems

        if (!restoreItems(
                needRestoreDataItems,
                extraItems,
                loadingDialog,
                fs,
                appManager,
                archivedApp,
                archiveItem,
                appInfo,
                packageName,
                userId,
                callback
            )
        ) return

        PermissionRestorer.restorePermissions(loadingDialog, archiveItem, fs, appManager, packageName, userId)
        RestoreRecordWriter.onRestoreSuccess(archivedApp, archiveItem)
        withContext(Dispatchers.Main) {
            loadingDialog.dismissItem()
            Toast.makeText(context, R.string.archive_restore_success, Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun restoreArchiveWithSelectedTypes(
        context: Context,
        fs: IFileSystem,
        appManager: IAppManager,
        archivedApp: ArchivedApp,
        archiveItem: ArchiveItem,
        loadingDialog: IItemProgressDialog,
        selectedTypes: Set<String>
    ) {
        validateRestoreUserId(archiveItem)

        val appInfo = archiveItem.appInfo
        val packageName = appInfo.packageName
        val userId = appInfo.userId

        val callback = itemCallback(loadingDialog, context)

        val allDataItems = archiveItem.dataItems.toMutableList()
        val dataItems = allDataItems.filter { selectedTypes.contains(it.name) }

        val selectedExtraItems =
            archiveItem.extraItems.filterKeys { selectedTypes.contains(it.name) }
        Log.i(TAG, "高级恢复：选中 ${selectedExtraItems.size} 个额外项目")

        if (dataItems.isEmpty() && selectedExtraItems.isEmpty()) {
            withContext(Dispatchers.Main) {
                loadingDialog.dismissItem()
                Toast.makeText(context, R.string.archive_no_selected_data, Toast.LENGTH_SHORT).show()
            }
            return
        }

        val needRestoreApk = selectedTypes.contains(CompressItems.COMPRESS_ITEM_APK)
        val installed = appManager.isInstalled(packageName, userId)

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
                    ApksUtil.calculateInstalledApkSize(fs, installedApkInfo)
                } else {
                    0L
                }

                if (installedVersionCode == archiveVersionCode && installedApkSize == archiveApkSize) {
                    needInstallApk = false
                    Log.i(TAG, "高级恢复: 跳过APK安装，版本相同")
                }
            }
        }

        val hasDataTypes = selectedTypes.any { it != CompressItems.COMPRESS_ITEM_APK }
        if (hasDataTypes && !installed && !needInstallApk) {
            withContext(Dispatchers.Main) {
                loadingDialog.dismissItem()
                Toast.makeText(context, R.string.archive_app_not_installed_restore_apk, Toast.LENGTH_LONG)
                    .show()
            }
            return
        }

        if (hasDataTypes && installed) {
            withContext(Dispatchers.Main) {
                loadingDialog.setItemMessage(context.getString(R.string.archive_clearing_app_data))
                loadingDialog.setItemStatus("...")
            }
            appManager.clearAppData(packageName, userId)
        }

        val needRestoreDataItems = dataItems.toMutableList()
        val apkDataItem = needRestoreDataItems.find { it.name == CompressItems.COMPRESS_ITEM_APK }

        if (apkDataItem != null) {
            if (!needInstallApk) {
                needRestoreDataItems.remove(apkDataItem)
            } else {
                needRestoreDataItems.remove(apkDataItem)
                val apkDataItemDir = ArchivedApks.getArchivedApkDir(
                    archivedApp.packageDir,
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
                archivedApp,
                archiveItem,
                appInfo,
                packageName,
                userId,
                callback
            )
        ) return

        if (needInstallApk || hasDataTypes) {
            PermissionRestorer.restorePermissions(loadingDialog, archiveItem, fs, appManager, packageName, userId)
        }

        RestoreRecordWriter.onRestoreSuccess(archivedApp, archiveItem)
        withContext(Dispatchers.Main) {
            loadingDialog.dismissItem()
            Toast.makeText(context, R.string.archive_advanced_restore_success, Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun restoreItems(
        needRestoreDataItems: MutableList<MetaDataItem>,
        extraItems: Map<MetaDataItem, String>,
        loadingDialog: IItemProgressDialog,
        fs: IFileSystem,
        appManager: IAppManager,
        archivedApp: ArchivedApp,
        archiveItem: ArchiveItem,
        appInfo: AppInfo,
        packageName: String,
        userId: Int,
        callback: DataItemCallback
    ): Boolean {
        var currentIndex = 0
        val totalItems = needRestoreDataItems.size + extraItems.size

        try {
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
                    archivedApp,
                    archiveItem,
                    dataItem,
                    appInfo,
                    packageName,
                    userId,
                    callback,
                    loadingDialog
                )
            }

            if (extraItems.isNotEmpty()) {
                Log.i(TAG, "开始恢复 ${extraItems.size} 个额外项目")
                for ((extraDataItem, extraPath) in extraItems) {
                    currentIndex++
                    val progress = (currentIndex * 100) / totalItems
                    val itemName = extraDataItem.name

                    withContext(Dispatchers.Main) {
                        loadingDialog.setCurrentItem("$itemName (额外)")
                        loadingDialog.setItemProgress(progress)
                    }
                    DataRestorer.restoreExtraItem(
                        fs,
                        archiveItem,
                        extraDataItem,
                        extraPath,
                        callback
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

    private suspend fun restoreDataItem(
        fs: IFileSystem,
        appManager: IAppManager,
        archivedApp: ArchivedApp,
        archiveItem: ArchiveItem,
        dataItem: MetaDataItem,
        appInfo: AppInfo,
        packageName: String,
        userId: Int,
        callback: DataItemCallback,
        loadingDialog: IItemProgressDialog
    ) {
        when (dataItem.name) {
            CompressItems.COMPRESS_ITEM_APK -> {
                ApkInstaller.installApks(
                    fs,
                    appManager,
                    archivedApp,
                    archiveItem,
                    dataItem,
                    packageName,
                    userId,
                    callback
                )
            }

            CompressItems.COMPRESS_ITEM_DATA -> {
                DataRestorer.restoreData(
                    fs,
                    appManager,
                    archiveItem,
                    dataItem,
                    appInfo.getPackageDataDir(),
                    callback
                )
            }

            CompressItems.COMPRESS_ITEM_USER -> {
                DataRestorer.restoreData(fs, appManager, archiveItem, dataItem, appInfo.getUserDir(), callback)
            }

            CompressItems.COMPRESS_ITEM_USER_DE -> {
                DataRestorer.restoreData(fs, appManager, archiveItem, dataItem, appInfo.getUserDeDir(), callback)
            }

            CompressItems.COMPRESS_ITEM_OBB -> {
                DataRestorer.restoreData(
                    fs,
                    appManager,
                    archiveItem,
                    dataItem,
                    appInfo.getPackageObbDir(),
                    callback
                )
            }

            CompressItems.COMPRESS_ITEM_MEDIA -> {
                DataRestorer.restoreData(
                    fs,
                    appManager,
                    archiveItem,
                    dataItem,
                    appInfo.getPackageMediaDir(),
                    callback
                )
            }

            else -> {}
        }
    }

    private fun itemCallback(
        loadingDialog: IItemProgressDialog,
        context: Context
    ): DataItemCallback {
        return object : DataItemCallback {
            override fun onStart(dataItem: MetaDataItem) {
                loadingDialog.post {
                    loadingDialog.setItemStatus(context.getString(R.string.ellipsis))
                }
            }

            override fun onProgress(dataItem: MetaDataItem, bytesWritten: Long, bytesPerS: Long) {
                loadingDialog.post {
                    val fileSize = Formatter.formatFileSize(context, bytesWritten)
                    loadingDialog.setItemMessage(context.getString(R.string.archive_read_progress, fileSize))
                    if (bytesPerS == 0L) {
                        loadingDialog.setItemStatus(context.getString(R.string.ellipsis))
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
    }
}

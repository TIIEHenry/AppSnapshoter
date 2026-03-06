package tiiehenry.android.app.snapshotor.data

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import tiiehenry.android.app.snapshotor.app.AppInfo
import tiiehenry.android.app.snapshotor.config.AppConfig
import tiiehenry.android.app.snapshotor.config.CompressItems
import tiiehenry.android.app.snapshotor.config.GroupConfig
import tiiehenry.android.app.snapshotor.group.SnapedApp
import tiiehenry.android.app.snapshotor.util.ApkUtil
import tiiehenry.android.snapshotor.app.IAppManager
import tiiehenry.android.snapshotor.file.ICompressCallback
import tiiehenry.android.snapshotor.file.IFileSystem
import tiiehenry.android.snapshotor.fs.CompressState
import tiiehenry.android.snapshotor.fs.IFileType
import tiiehenry.android.snapshotor.task.ITaskHandler
import java.io.File
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.absolutePathString
import kotlin.io.path.isRegularFile

object SnapShotMaker {

    fun makeSnapshot(
        fileSystem: IFileSystem,
        appManager: IAppManager,
        snapedApp: SnapedApp,
        appInfo: AppInfo,
        callback: ICompressCallback,
        groupConfig: GroupConfig,
        appConfig: AppConfig,
        archiveName: String? = null
    ): java.util.LinkedHashMap<String, ITaskHandler>? {
        try {
            val rootPath = groupConfig.rootPath
            val packageDir = Paths.get(rootPath, appInfo.packageName).absolutePathString()

            if (fileSystem.fileType(packageDir) == IFileType.TYPE_NONE) {
                fileSystem.mkdirs(packageDir)
            }

            // 生成存档名称
            val name = archiveName ?: generateArchiveName()
            val archiveDir = Paths.get(packageDir, name).absolutePathString()

            fileSystem.mkdirs(archiveDir)

            // 获取要压缩的项目
            val compressItems = if (appConfig.shotConfig.hasCompressItems()) {
                appConfig.shotConfig.compressItems
            } else {
                groupConfig.shotConfig.compressItems
            }
            val compressAlgorithm = if (appConfig.shotConfig.hasCompressAlgorithm()) {
                appConfig.shotConfig.compressAlgorithm
            } else {
                groupConfig.shotConfig.compressAlgorithm
            }
            val tasks = LinkedHashMap<String, ITaskHandler>()
            val applicationInfo =
                appInfo.getApplicationInfo(appManager) ?: throw IllegalStateException(
                    "ApplicationInfo is null"
                )
            val packageInfo = appInfo.getPackageInfo(appManager) ?: throw IllegalStateException(
                "PackageInfo is null"
            )
            val handler =
                createMetaInfoTask(appInfo, appManager, packageInfo, applicationInfo, compressItems, archiveDir)
            tasks["meta-info"] = handler
            val compressor = fileSystem.compressor
            val algorithm = compressAlgorithm.ifEmpty {
                compressor.supportedAlgorithms().first()
            }
            val dataItems = mutableListOf<MetaDataItem>()
            for (item in compressItems) {
                when (item) {
                    CompressItems.COMPRESS_ITEM_APK -> {
                        val apkDataItemDir = ArchivedApks.getArchivedApkDir(
                            snapedApp.packageDir,
                            packageInfo.longVersionCode
                        )
                        val apkPath = applicationInfo.publicSourceDir
                        val extension = compressor.fileExtension(algorithm, item, apkPath)
                        val id = ApkUtil.calculateInstalledApkSize(fileSystem, applicationInfo).toString()
                        val fileName = "$id$extension"
                        //使用版本号作为文件夹名
                        //文件大小作为文件名唯一标识
                        val filePath = Paths.get(apkDataItemDir, fileName)
                        if (filePath.isRegularFile()) {
                            continue
                        }
                        val apks=mutableListOf<String>()
                        apks.add(apkPath)
                        applicationInfo.splitPublicSourceDirs?.forEach { apks.add(it) }
                        val task = compressor.compressMultiple(
                            algorithm,
                            apks,
                            filePath.absolutePathString(),
                            createCompressCallback(
                                callback,
                                dataItems,
                                algorithm,
                                fileName,
                                id,
                                apkDataItemDir
                            )
                        )
                        tasks[item] = task
                    }

                    CompressItems.COMPRESS_ITEM_DATA -> {
                        val dataPath = appInfo.getPackageDataDir()
                        if (fileSystem.fileType(dataPath) != IFileType.TYPE_NONE) {
                            val extension = compressor.fileExtension(algorithm, item, dataPath)
                            val fileName = "data$extension"
                            val task = compressor.compress(
                                algorithm,
                                dataPath,
                                Paths.get(archiveDir, fileName).absolutePathString(),
                                arrayListOf(),
                                arrayListOf(),
                                createCompressCallback(
                                    callback,
                                    dataItems,
                                    algorithm,
                                    fileName,
                                    item,
                                    archiveDir
                                )
                            )
                            tasks[item] = task
                        }
                    }

                    CompressItems.COMPRESS_ITEM_USER -> {
                        val userPath = appInfo.getUserDir()
                        if (fileSystem.fileType(userPath) != IFileType.TYPE_NONE) {
                            val extension = compressor.fileExtension(algorithm, item, userPath)
                            val fileName = "user$extension"
                            val task = compressor.compress(
                                algorithm,
                                userPath,
                                Paths.get(archiveDir, fileName).absolutePathString(),
                                arrayListOf(),
                                arrayListOf(),
                                createCompressCallback(
                                    callback,
                                    dataItems,
                                    algorithm,
                                    fileName,
                                    item,
                                    archiveDir
                                )
                            )
                            tasks[item] = task
                        }
                    }

                    CompressItems.COMPRESS_ITEM_USER_DE -> {
                        val userDePath = appInfo.getUserDeDir()
                        if (fileSystem.fileType(userDePath) != IFileType.TYPE_NONE) {
                            val extension =
                                compressor.fileExtension(algorithm, item, userDePath)
                            val fileName = "user_de$extension"
                            val task = compressor.compress(
                                algorithm,
                                userDePath,
                                Paths.get(archiveDir, fileName).absolutePathString(),
                                arrayListOf(),
                                arrayListOf(),
                                createCompressCallback(
                                    callback,
                                    dataItems,
                                    algorithm,
                                    fileName,
                                    item,
                                    archiveDir
                                )
                            )
                            tasks[item] = task
                        }
                    }

                    CompressItems.COMPRESS_ITEM_OBB -> {
                        val obbPath = appInfo.getPackageObbDir()
                        if (fileSystem.fileType(obbPath) != IFileType.TYPE_NONE) {
                            val extension = compressor.fileExtension(algorithm, item, obbPath)
                            val fileName = "obb$extension"
                            val task = compressor.compress(
                                algorithm,
                                obbPath,
                                Paths.get(archiveDir, fileName).absolutePathString(),
                                arrayListOf(),
                                arrayListOf(),
                                createCompressCallback(
                                    callback,
                                    dataItems,
                                    algorithm,
                                    fileName,
                                    item,
                                    archiveDir
                                )
                            )
                            tasks[item] = task
                        }
                    }

                    CompressItems.COMPRESS_ITEM_MEDIA -> {
                        val externalDataPath = appInfo.getPackageMediaDir()
                        if (fileSystem.fileType(externalDataPath) != IFileType.TYPE_NONE) {
                            val extension =
                                compressor.fileExtension(algorithm, item, externalDataPath)
                            val fileName = "media$extension"
                            val task = compressor.compress(
                                algorithm,
                                externalDataPath,
                                Paths.get(archiveDir, fileName).absolutePathString(),
                                arrayListOf(),
                                arrayListOf(),
                                createCompressCallback(
                                    callback,
                                    dataItems,
                                    algorithm,
                                    fileName,
                                    item,
                                    archiveDir
                                )
                            )
                            tasks[item] = task
                        }
                    }
                }
            }
            return tasks
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun createMetaInfoTask(
        appInfo: AppInfo,
        appManager: IAppManager,
        packageInfo: PackageInfo,
        applicationInfo: ApplicationInfo,
        compressItems: Set<String>,
        archiveDir: String
    ): ITaskHandler.Stub {
        val makeTime = System.currentTimeMillis()
        val handler = object : ITaskHandler.Stub() {
            var state = CompressState.COMPRESS_STATE_NONE
            var isCancel = AtomicBoolean(false)
            override fun id(): String {
                return "meta-info"
            }

            override fun state(): Int {
                return state
            }

            override fun start() {
                state = CompressState.COMPRESS_STATE_RUNNING
                try {
                    // 这里只创建meta-info.json
                    val metaPackageInfo = MetaPackageInfo(
                        appInfo.loadLabel(appManager) ?: appInfo.packageName,
                        appInfo.packageName,
                        packageInfo.longVersionCode,
                        packageInfo.versionName,
                        packageInfo.firstInstallTime,
                        applicationInfo.flags,
                        packageInfo.lastUpdateTime,
                        ApkUtil.calculateInstalledApkSize(appInfo.fs, applicationInfo)
                    )
                    val permissions = appInfo.getPermissions(appManager)
                        .map { MetaPermission.fromAppPermission(it) }
                    val dataItems = compressItems.map { "${it}.json" }
                    val uid = appManager.getPackageUid(appInfo.packageName, appInfo.userId)
                    val ssaid = try {
                        appManager.getPackageSsaidAsUser(appInfo.packageName, uid, appInfo.userId) ?: ""
                    } catch (e: Exception) {
                        e.printStackTrace()
                        ""
                    }
                    val metaInfo = MetaInfo(
                        metaPackageInfo,
                        appInfo.userId,
                        ssaid,
                        dataItems,
                        permissions,
                        makeTime
                    )

                    // 分别保存 meta-info.json（使用compressItems）、permissions.json 和各个 data-item.json
                    MetaInfoHelper.writeToArchive(metaInfo, File(archiveDir))
                    MetaInfoHelper.writePermissions(
                        permissions,
                        File(archiveDir)
                    )
                    state = CompressState.COMPRESS_STATE_COMPLETE
                } catch (e: Exception) {
                    e.printStackTrace()
                    state = CompressState.COMPRESS_STATE_ERROR
                }
            }

            override fun cancel() {
                isCancel.set(true)
            }
        }
        return handler
    }

    private fun createPermissionTask(
        appInfo: AppInfo,
        appManager: IAppManager,
        packageInfo: PackageInfo,
        applicationInfo: ApplicationInfo,
        compressItems: Set<String>,
        archiveDir: String
    ): ITaskHandler.Stub {
        val handler = object : ITaskHandler.Stub() {
            var state = CompressState.COMPRESS_STATE_NONE
            var isCancel = AtomicBoolean(false)
            override fun id(): String {
                return "permissions"
            }

            override fun state(): Int {
                return state
            }

            override fun start() {
                state = CompressState.COMPRESS_STATE_RUNNING
                try {
                    val permissions = appInfo.getPermissions(appManager)
                        .map { MetaPermission.fromAppPermission(it) }
                    MetaInfoHelper.writePermissions(
                        permissions,
                        File(archiveDir)
                    )
                    state = CompressState.COMPRESS_STATE_COMPLETE
                } catch (e: Exception) {
                    e.printStackTrace()
                    state = CompressState.COMPRESS_STATE_ERROR
                }
            }

            override fun cancel() {
                isCancel.set(true)
            }
        }
        return handler
    }

    private fun generateArchiveName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return dateFormat.format(Date())
    }

    private fun createCompressCallback(
        callback: ICompressCallback,
        dataItems: MutableList<MetaDataItem>,
        algorithm: String,
        fileName: String,
        itemType: String,
        archiveDir: String
    ): ICompressCallback {
        return object : ICompressCallback.Stub() {
            var startTime = 0L
            override fun onStart() {
                startTime = System.currentTimeMillis()
                callback.onStart()
            }

            override fun onProgress(
                bytesWritten: Long,
                kbPerS: Long
            ) {
                callback.onProgress(bytesWritten, kbPerS)
            }

            override fun onDone(
                originSize: Long,
                targetSize: Long,
                md5: String
            ) {
                callback.onDone(originSize, targetSize, md5)
                val endTime = System.currentTimeMillis()
                val dataItem = MetaDataItem(
                    algorithm,
                    itemType,
                    fileName,
                    originSize,
                    targetSize,
                    md5,
                    endTime - startTime,
                    System.currentTimeMillis()
                )
                dataItems.add(dataItem)
                MetaInfoHelper.saveDataItem(dataItem, File(archiveDir))
            }

            override fun onError(msg: String?) {
                callback.onError(msg)
            }
        }
    }

    fun deleteArchive(archivePath: String): Boolean {
        return try {
            val archiveDir = File(archivePath)
            if (archiveDir.exists()) {
                archiveDir.deleteRecursively()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun restoreArchive(archivePath: String): Boolean {
        // TODO: 实现恢复逻辑，需要调用IFileSystem接口
        return false
    }
}

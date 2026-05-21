package tiiehenry.android.app.snapshot.archive.make

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.util.Log
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.config.AppConfig
import tiiehenry.android.app.snapshot.config.CompressItems
import tiiehenry.android.app.snapshot.config.GroupConfig
import tiiehenry.android.app.snapshot.archive.ArchivedApks
import tiiehenry.android.app.snapshot.archive.MetaInfoHelper
import tiiehenry.android.app.snapshot.archive.bean.MetaDataItem
import tiiehenry.android.app.snapshot.archive.bean.MetaInfo
import tiiehenry.android.app.snapshot.archive.bean.MetaPackageInfo
import tiiehenry.android.app.snapshot.archive.bean.MetaPermission
import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.app.snapshot.utils.ApksUtil
import tiiehenry.android.snapshot.app.IAppManager
import tiiehenry.android.snapshot.file.ICompressCallback
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.fs.CompressState
import tiiehenry.android.snapshot.fs.IFileType
import tiiehenry.android.snapshot.task.ITaskHandler
import java.io.File
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.absolutePathString
import kotlin.io.path.isRegularFile

object ArchiveMaker {

    fun makeSnapshot(
        fileSystem: IFileSystem,
        appManager: IAppManager,
        archivedApp: ArchivedApp,
        appInfo: AppInfo,
        callback: ICompressCallback,
        groupConfig: GroupConfig,
        appConfig: AppConfig,
        archiveName: String? = null
    ): SnapshotTasks? {
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
            val shotConfig = if (appConfig.shotConfig.enabled) {
                appConfig.shotConfig
            } else {
                groupConfig.shotConfig
            }
            val actionConfig = if (appConfig.actionConfig.enabled) {
                appConfig.actionConfig
            } else {
                groupConfig.actionConfig
            }
            val excludeConfig = if (appConfig.shotConfig.enabled) {
                appConfig.excludeConfig
            } else {
                groupConfig.excludeConfig
            }
            // 获取要压缩的项目
            val compressItems = shotConfig.items
            val compressAlgorithm = actionConfig.compressAlgorithm

            val compressLevel = actionConfig.compressLevel
            val tasks = LinkedHashMap<String, ITaskHandler>()
            val applicationInfo =
                appInfo.getApplicationInfo(appManager) ?: throw IllegalStateException(
                    "ApplicationInfo is null"
                )
            val packageInfo = appInfo.getPackageInfo(appManager) ?: throw IllegalStateException(
                "PackageInfo is null"
            )
            val compressor = fileSystem.compressor
            val algorithm = compressAlgorithm.ifEmpty {
                compressor.supportedAlgorithms().first()
            }
            val dataItems = mutableListOf<MetaDataItem>()
            val extraItemsMap = mutableMapOf<String, String>()

            // 获取排除模式映射（优先使用应用配置，否则使用组配置）
            val excludePatternsMap = excludeConfig.getItemExcludePatternsMap()

            val realCompressItems = mutableSetOf<String>()
            val handler =
                createMetaInfoTask(
                    appInfo,
                    appManager,
                    packageInfo,
                    applicationInfo,
                    realCompressItems,
                    extraItemsMap,
                    archiveDir
                )
            tasks["meta-info"] = handler
            // 处理额外压缩项目
            appConfig.extraItems.filter { it.isEnabled }.forEach { extraItem ->
                val name = extraItem.getName()
                val path = extraItem.getPath()
                val excludes = extraItem.getExcludePatterns()

                if (fileSystem.fileType(path) != IFileType.TYPE_NONE) {
                    val extension = compressor.fileExtension(algorithm, name, path)
                    val fileName = "$name$extension"
                    val task = compressor.compress(
                        algorithm,
                        path,
                        Paths.get(archiveDir, fileName).absolutePathString(),
                        excludes,
                        arrayListOf(),
                        compressLevel,
                        createCompressCallback(
                            callback,
                            dataItems,
                            algorithm,
                            compressLevel,
                            fileName,
                            name,
                            archiveDir
                        )
                    )
                    tasks[name] = task
                    extraItemsMap["$name.json"] = path
                }
            }

            for (item in compressItems) {
                when (item) {
                    CompressItems.COMPRESS_ITEM_APK -> {
                        val apkDataItemDir = ArchivedApks.getArchivedApkDir(
                            archivedApp.packageDir,
                            packageInfo.longVersionCode
                        )
                        val apkPath = applicationInfo.publicSourceDir
                        val extension = compressor.fileExtension(algorithm, item, apkPath)
                        val id = ApksUtil.calculateInstalledApkSize(fileSystem, applicationInfo)
                            .toString()
                        val fileName = "$id$extension"
                        //使用版本号作为文件夹名
                        //文件大小作为文件名唯一标识
                        val filePath = Paths.get(apkDataItemDir, fileName)
                        if (filePath.isRegularFile()) {
                            continue
                        }
                        val compressLevel = 3// apk use static compress level 高level 压缩不了太多内存了
                        val apks = mutableListOf<String>()
                        apks.add(apkPath)
                        applicationInfo.splitPublicSourceDirs?.forEach { apks.add(it) }
                        val task = compressor.compressMultiple(
                            algorithm,
                            apks,
                            filePath.absolutePathString(),
                            compressLevel,
                            createCompressCallback(
                                callback,
                                dataItems,
                                algorithm,
                                compressLevel,
                                fileName,
                                id,
                                apkDataItemDir
                            )
                        )
                        tasks[item] = task
                        realCompressItems.add(item)
                    }

                    CompressItems.COMPRESS_ITEM_DATA -> {
                        val dataPath = appInfo.getPackageDataDir()
                        if (fileSystem.fileType(dataPath) != IFileType.TYPE_NONE) {
                            val extension = compressor.fileExtension(algorithm, item, dataPath)
                            val fileName = "data$extension"
                            val excludes = excludePatternsMap[CompressItems.COMPRESS_ITEM_DATA]
                                ?: arrayListOf()
                            val task = compressor.compress(
                                algorithm,
                                dataPath,
                                Paths.get(archiveDir, fileName).absolutePathString(),
                                excludes,
                                arrayListOf(),
                                compressLevel,
                                createCompressCallback(
                                    callback,
                                    dataItems,
                                    algorithm,
                                    compressLevel,
                                    fileName,
                                    item,
                                    archiveDir
                                )
                            )
                            tasks[item] = task
                            realCompressItems.add(item)
                        }
                    }

                    CompressItems.COMPRESS_ITEM_USER -> {
                        val userPath = appInfo.getUserDir()
                        if (fileSystem.fileType(userPath) != IFileType.TYPE_NONE) {
                            val extension = compressor.fileExtension(algorithm, item, userPath)
                            val fileName = "user$extension"
                            val excludes = excludePatternsMap[CompressItems.COMPRESS_ITEM_USER]
                                ?: arrayListOf()
                            val task = compressor.compress(
                                algorithm,
                                userPath,
                                Paths.get(archiveDir, fileName).absolutePathString(),
                                excludes,
                                arrayListOf(),
                                compressLevel,
                                createCompressCallback(
                                    callback,
                                    dataItems,
                                    algorithm,
                                    compressLevel,
                                    fileName,
                                    item,
                                    archiveDir
                                )
                            )
                            tasks[item] = task
                            realCompressItems.add(item)
                        }
                    }

                    CompressItems.COMPRESS_ITEM_USER_DE -> {
                        val userDePath = appInfo.getUserDeDir()
                        if (fileSystem.fileType(userDePath) != IFileType.TYPE_NONE) {
                            val extension =
                                compressor.fileExtension(algorithm, item, userDePath)
                            val fileName = "user_de$extension"
                            val excludes = excludePatternsMap[CompressItems.COMPRESS_ITEM_USER_DE]
                                ?: arrayListOf()
                            val task = compressor.compress(
                                algorithm,
                                userDePath,
                                Paths.get(archiveDir, fileName).absolutePathString(),
                                excludes,
                                arrayListOf(),
                                compressLevel,
                                createCompressCallback(
                                    callback,
                                    dataItems,
                                    algorithm,
                                    compressLevel,
                                    fileName,
                                    item,
                                    archiveDir
                                )
                            )
                            tasks[item] = task
                            realCompressItems.add(item)
                        }
                    }

                    CompressItems.COMPRESS_ITEM_OBB -> {
                        val obbPath = appInfo.getPackageObbDir()
                        if (fileSystem.fileType(obbPath) != IFileType.TYPE_NONE) {
                            val extension = compressor.fileExtension(algorithm, item, obbPath)
                            val fileName = "obb$extension"
                            val excludes =
                                excludePatternsMap[CompressItems.COMPRESS_ITEM_OBB] ?: arrayListOf()
                            val task = compressor.compress(
                                algorithm,
                                obbPath,
                                Paths.get(archiveDir, fileName).absolutePathString(),
                                excludes,
                                arrayListOf(),
                                compressLevel,
                                createCompressCallback(
                                    callback,
                                    dataItems,
                                    algorithm,
                                    compressLevel,
                                    fileName,
                                    item,
                                    archiveDir
                                )
                            )
                            tasks[item] = task
                            realCompressItems.add(item)
                        }
                    }

                    CompressItems.COMPRESS_ITEM_MEDIA -> {
                        val externalDataPath = appInfo.getPackageMediaDir()
                        if (fileSystem.fileType(externalDataPath) != IFileType.TYPE_NONE) {
                            val extension =
                                compressor.fileExtension(algorithm, item, externalDataPath)
                            val fileName = "media$extension"
                            val excludes = excludePatternsMap[CompressItems.COMPRESS_ITEM_MEDIA]
                                ?: arrayListOf()
                            val task = compressor.compress(
                                algorithm,
                                externalDataPath,
                                Paths.get(archiveDir, fileName).absolutePathString(),
                                excludes,
                                arrayListOf(),
                                compressLevel,
                                createCompressCallback(
                                    callback,
                                    dataItems,
                                    algorithm,
                                    compressLevel,
                                    fileName,
                                    item,
                                    archiveDir
                                )
                            )
                            tasks[item] = task
                            realCompressItems.add(item)
                        }
                    }
                }
            }

            if (!groupConfig.isLocked(appInfo.packageName) && actionConfig.isUninstallArchived) {
                val uninstallTask = createUninstallAppTask(appInfo, appManager)
                tasks["uninstall-app"] = uninstallTask
            }

            return SnapshotTasks(archiveDir, tasks)
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
        extraItemsMap: Map<String, String>,
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
                        ApksUtil.calculateInstalledApkSize(appInfo.fs, applicationInfo)
                    )
                    val permissions = appInfo.getPermissions(appManager)
                        .map { MetaPermission.fromAppPermission(it) }
                    val dataItems = compressItems.map { "${it}.json" }
                    val uid = appManager.getPackageUid(appInfo.packageName, appInfo.userId)
                    val ssaid = try {
                        appManager.getPackageSsaidAsUser(appInfo.packageName, uid, appInfo.userId)
                            ?: ""
                    } catch (e: Exception) {
                        e.printStackTrace()
                        ""
                    }
                    val metaInfo = MetaInfo(
                        metaPackageInfo,
                        appInfo.userId,
                        ssaid,
                        dataItems,
                        extraItemsMap,
                        permissions,
                        makeTime,
                        false
                    )

                    // 分别保存 meta-info.json（使用 compressItems）、permissions.json 和各个 data-item.json
                    MetaInfoHelper.writeToArchive(metaInfo, File(archiveDir))
                    MetaInfoHelper.writePermissions(
                        permissions,
                        File(archiveDir)
                    )
                    state = CompressState.COMPRESS_STATE_COMPLETE
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("SnapShotMaker", "MetaInfo task error: ${e.message}", e)
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
                    Log.e("SnapShotMaker", "Permission task error: ${e.message}", e)
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
        compressLevel: Int,
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
                bytesPerS: Long
            ) {
                callback.onProgress(bytesWritten, bytesPerS)
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
                    System.currentTimeMillis(),
                    compressLevel
                )
                dataItems.add(dataItem)
                MetaInfoHelper.saveDataItem(dataItem, File(archiveDir))
            }

            override fun onError(msg: String?) {
                callback.onError(msg)
            }
        }
    }

    private fun createUninstallAppTask(
        appInfo: AppInfo,
        appManager: IAppManager
    ): ITaskHandler.Stub {
        val handler = object : ITaskHandler.Stub() {
            var state = CompressState.COMPRESS_STATE_NONE
            var isCancel = AtomicBoolean(false)

            override fun id(): String {
                return "uninstall-app"
            }

            override fun state(): Int {
                return state
            }

            override fun start() {
                state = CompressState.COMPRESS_STATE_RUNNING
                try {
                    // 执行卸载操作
                    val success = appManager.uninstallApk(appInfo.packageName, appInfo.userId)
                    if (success) {
                        state = CompressState.COMPRESS_STATE_COMPLETE
                    } else {
                        Log.e(
                            "SnapShotMaker",
                            "Uninstall app failed: ${appInfo.packageName}"
                        )
                        state = CompressState.COMPRESS_STATE_ERROR
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("SnapShotMaker", "Uninstall app error: ${e.message}", e)
                    state = CompressState.COMPRESS_STATE_ERROR
                }
            }

            override fun cancel() {
                isCancel.set(true)
            }
        }
        return handler
    }

}

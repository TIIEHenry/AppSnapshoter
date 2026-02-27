package tiiehenry.android.app.snapshotor.data

import android.os.ParcelFileDescriptor
import android.util.Log
import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONWriter
import tiiehenry.android.app.snapshotor.app.AppInfo
import tiiehenry.android.app.snapshotor.config.AppConfig
import tiiehenry.android.app.snapshotor.config.CompressItems
import tiiehenry.android.app.snapshotor.config.GroupConfig
import tiiehenry.android.snapshotor.app.IAppManager
import tiiehenry.android.snapshotor.file.ICompressCallback
import tiiehenry.android.snapshotor.file.IFileSystem
import tiiehenry.android.snapshotor.fs.CompressState
import tiiehenry.android.snapshotor.fs.IFileType
import tiiehenry.android.snapshotor.task.ITaskHandler
import java.io.File
import java.io.FileWriter
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.absolutePathString

object SnapShotMaker {

    fun makeSnapshot(
        fileSystem: IFileSystem,
        appManager: IAppManager,
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
            val compressItems = if (appConfig.shotConfig.hasCompressItems) {
                appConfig.shotConfig.compressItems
            } else {
                groupConfig.shotConfig.compressItems
            }
            val compressAlgorithm = if (appConfig.shotConfig.hasCompressAlgorithm) {
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
            val compressor = fileSystem.compressor
            val algorithm = if (compressAlgorithm.isEmpty()) {
                compressor.supportedAlgorithms().first()
            } else {
                compressAlgorithm
            }
            val dataItems = mutableListOf<MetaDataItem>()
            for (item in compressItems) {
                when (item) {
                    CompressItems.COMPRESS_ITEM_APK -> {
                        val apkPath = applicationInfo.publicSourceDir
                        val extension = compressor.fileExtension(algorithm, item, apkPath)
                        val fileName = "apks.$extension"
                        val task = compressor.compress(
                            algorithm,
                            apkPath,
                            Paths.get(archiveDir, fileName).absolutePathString(),
                            arrayListOf(),
                            arrayListOf(),
                            object : ICompressCallback.Stub() {
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
                                        name,
                                        fileName,
                                        "install",
                                        originSize,
                                        targetSize,
                                        md5,
                                        endTime - startTime
                                    )
                                    dataItems.add(dataItem)
                                }

                                override fun onError(msg: String?) {
                                    callback.onError(msg)
                                }
                            }
                        )
                        tasks[item] = task
                    }

                    CompressItems.COMPRESS_ITEM_DATA -> {
                        val dataPath = appInfo.getDataDir()
                        if (fileSystem.fileType(dataPath) != IFileType.TYPE_NONE) {
                            val extension = compressor.fileExtension(algorithm, item, dataPath)
                            val fileName = "data.$extension"
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
                                    name,
                                    fileName,
                                    "data"
                                )
                            )
                            tasks[item] = task
                        }
                    }

                    CompressItems.COMPRESS_ITEM_USER -> {
                        val userPath = appInfo.getUserDir()
                        if (fileSystem.fileType(userPath) != IFileType.TYPE_NONE) {
                            val extension = compressor.fileExtension(algorithm, item, userPath)
                            val fileName = "user.$extension"
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
                                    name,
                                    fileName,
                                    "user"
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
                            val fileName = "user_de.$extension"
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
                                    name,
                                    fileName,
                                    "user_de"
                                )
                            )
                            tasks[item] = task
                        }
                    }

                    CompressItems.COMPRESS_ITEM_OBB -> {
                        val obbPath = appInfo.getObbDir()
                        if (fileSystem.fileType(obbPath) != IFileType.TYPE_NONE) {
                            val extension = compressor.fileExtension(algorithm, item, obbPath)
                            val fileName = "obb.$extension"
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
                                    name,
                                    fileName,
                                    "obb"
                                )
                            )
                            tasks[item] = task
                        }
                    }

                    CompressItems.COMPRESS_ITEM_EXTERNAL_DATA -> {
                        val externalDataPath = appInfo.getExternalDataDir()
                        if (fileSystem.fileType(externalDataPath) != IFileType.TYPE_NONE) {
                            val extension =
                                compressor.fileExtension(algorithm, item, externalDataPath)
                            val fileName = "external_data.$extension"
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
                                    name,
                                    fileName,
                                    "external_data"
                                )
                            )
                            tasks[item] = task
                        }
                    }
                }
            }
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
                            packageInfo.lastUpdateTime
                        )
                        val metaInfo = MetaInfo(
                            metaPackageInfo,
                            appInfo.userId,
                            dataItems,
                            appInfo.getPermissions(appManager)
                                .map { MetaPermission.fromAppPermission(it) },
                            TimeInfo(
                                dataItems.sumOf { it.compressCost },
                                System.currentTimeMillis()
                            )
                        )
                        for (item in metaInfo.permissions) {
                            Log.i("SnapShotMaker", "metaInfo: $item")
                        }
                        val jsonString =
                            JSON.toJSONString(metaInfo, JSONWriter.Feature.PrettyFormat)
                        if (isCancel.get()) {
                            state = CompressState.COMPRESS_STATE_CANCELED
                            return
                        }
                        val metaFile = File(archiveDir, "meta-info.json")
                        val fileDescriptor =
                            fileSystem.openFile(
                                metaFile.absolutePath,
                                ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE
                            )
                        fileDescriptor.use {
                            FileWriter(it.fileDescriptor).use {
                                it.write(jsonString)
                            }
                        }
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
            tasks["meta-info"] = handler
            return tasks
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun generateArchiveName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return dateFormat.format(Date())
    }

    private fun createCompressCallback(
        callback: ICompressCallback,
        dataItems: MutableList<MetaDataItem>,
        algorithm: String,
        archiveName: String,
        fileName: String,
        itemType: String
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
                    archiveName,
                    fileName,
                    itemType,
                    originSize,
                    targetSize,
                    md5,
                    endTime - startTime
                )
                dataItems.add(dataItem)
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

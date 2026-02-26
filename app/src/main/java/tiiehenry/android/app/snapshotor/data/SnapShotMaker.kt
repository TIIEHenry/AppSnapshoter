package tiiehenry.android.app.snapshotor.data

import android.os.ParcelFileDescriptor
import com.alibaba.fastjson2.JSON
import com.tencent.mmkv.MMKV
import tiiehenry.android.snapshotor.fs.IFileType
import tiiehenry.android.snapshotor.app.IAppManager
import tiiehenry.android.snapshotor.file.ICompressCallback
import tiiehenry.android.snapshotor.file.IFileSystem
import tiiehenry.android.snapshotor.fs.CompressState
import tiiehenry.android.snapshotor.task.ITaskHandler
import tiiehenry.android.app.snapshotor.app.AppInfo
import tiiehenry.android.app.snapshotor.config.AppConfig
import tiiehenry.android.app.snapshotor.config.GroupConfig
import java.io.File
import java.io.FileWriter
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.absolutePathString

class SnapShotMaker {

    companion object {
        fun makeSnapshot(
            fileSystem: IFileSystem,
            appManager: IAppManager,
            appInfo: AppInfo,
            callback: ICompressCallback,
            mmkv: MMKV,
            groupConfig: GroupConfig,
            appConfig: AppConfig? = null,
            archiveName: String? = null
        ): java.util.LinkedHashMap<String, ITaskHandler>? {
            try {
                val rootPath = groupConfig.rootPath
                val packageDir = Paths.get(rootPath, appInfo.packageName).absolutePathString()

                if (fileSystem.fileType(packageDir) == IFileType.TYPE_NONE) {
                    fileSystem.mkdirs(packageDir)
                }

                // 生成存档名称
                val name = archiveName ?: generateArchieveName()
                val archiveDir = Paths.get(packageDir, name).absolutePathString()

                if (fileSystem.fileType(packageDir) == IFileType.TYPE_DIR) {
                    throw IllegalStateException("Package directory already exists: $packageDir")
                }

                fileSystem.mkdirs(archiveDir)

                // 获取要压缩的项目
                val compressItems = appConfig?.shotConfig?.compressItems
                    ?: groupConfig.shotConfig.compressItems
                val tasks = LinkedHashMap<String, ITaskHandler>()
                val applicationInfo =
                    appInfo.getApplicationInfo(appManager) ?: throw IllegalStateException(
                        "ApplicationInfo is null"
                    )
                val packageInfo = appInfo.getPackageInfo(appManager) ?: throw IllegalStateException(
                    "PackageInfo is null"
                )
                val compressor = fileSystem.compressor
                val dataItems = mutableListOf<MetaDataItem>()
                val supportedAlgorithms = compressor.supportedAlgorithms()
                for (item in compressItems) {
                    var algorithm = mmkv.decodeString("algorithm.$item") ?: ""
                    when (item) {
                        "apk" -> {
                            if (algorithm !in supportedAlgorithms) {
                                algorithm = supportedAlgorithms.first()
                            }
                            val apkPath = applicationInfo.publicSourceDir
                            val extension = compressor.fileExtension(algorithm, item, apkPath)
                            val fileName = "$item.$extension"
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
                                        progress: Int,
                                        kbPerS: Long
                                    ) {
                                        callback.onProgress(progress, kbPerS)
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
                    }
//                    fileSystem.compress()
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
                                packageInfo = metaPackageInfo,
                                userId = appInfo.userId,
                                dataItems = dataItems,
                                permissions = appInfo.getPermissions(appManager)
                                    .map { MetaPermission.fromAppPermission(it) },
                                time = TimeInfo(
                                    dataItems.sumOf { it.compressCost },
                                    System.currentTimeMillis()
                                )
                            )
                            val jsonString = JSON.toJSONString(metaInfo)
                            if (isCancel.get()) {
                                state = CompressState.COMPRESS_STATE_CANCELED
                                return
                            }
                            val metaFile = File(archiveDir, "meta-info.json")
                            val fileDescriptor =
                                fileSystem.openFile(
                                    metaFile.absolutePath,
                                    ParcelFileDescriptor.MODE_WRITE_ONLY
                                )
                            fileDescriptor.use {
                                FileWriter(it.fileDescriptor).write(jsonString)
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

        private fun generateArchieveName(): String {
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            return dateFormat.format(Date())
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
}

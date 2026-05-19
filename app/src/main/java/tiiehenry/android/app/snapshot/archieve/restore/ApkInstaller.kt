package tiiehenry.android.app.snapshot.archieve.restore

import android.util.Log
import kotlinx.coroutines.delay
import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.archieve.ArchivedApks
import tiiehenry.android.app.snapshot.archieve.bean.MetaDataItem
import tiiehenry.android.app.snapshot.main.launch.exception.InstallFailedException
import tiiehenry.android.app.snapshot.main.launch.exception.MissingDataFileException
import tiiehenry.android.snapshot.app.IAppManager
import tiiehenry.android.snapshot.file.ICompressCallback
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.fs.CompressState
import java.nio.file.Paths

/**
 * APK 安装器 - 负责从存档中解压并安装 APK
 */
object ApkInstaller {

    private const val TAG = "ApkInstaller"

    suspend fun installApks(
        fs: IFileSystem,
        appManager: IAppManager,
        archivedApp: tiiehenry.android.app.snapshot.group.ArchivedApp,
        archiveItem: ArchiveItem,
        dataItem: MetaDataItem,
        packageName: String,
        userId: Int,
        callback: DataItemCallback
    ): Boolean {
        Log.i(TAG, "restoreApk: $packageName")
        val archiveDir = ArchivedApks.getArchivedApkDir(
            archivedApp.packageDir,
            archiveItem.metaInfo.packageInfo.versionCode
        )
        val archivedApksFile = Paths.get(archiveDir, dataItem.file).toString()

        if (!fs.exists(archivedApksFile)) {
            throw MissingDataFileException(dataItem, archivedApksFile)
        }

        val tempDir = fs.createTempFile("apk-", ".tmp")
        fs.delete(tempDir)
        fs.mkdirs(tempDir)

        var errorMsg: String? = null
        val compressCallback = object : ICompressCallback.Stub() {
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
                callback.onError(dataItem, tiiehenry.android.app.snapshot.main.launch.exception.RestoreFailedException(dataItem, errorMsg))
            }
        }

        try {
            val decompressor = fs.compressor
            val algorithm = dataItem.algorithm.ifEmpty { decompressor.detectAlgorithm(archivedApksFile) }
            val task = decompressor.decompress(algorithm, archivedApksFile, tempDir, compressCallback)
            task.start()

            var state = task.state()
            while (state == CompressState.COMPRESS_STATE_RUNNING || state == CompressState.COMPRESS_STATE_NONE) {
                delay(100)
                state = task.state()
            }

            if (state != CompressState.COMPRESS_STATE_COMPLETE) {
                throw tiiehenry.android.app.snapshot.main.launch.exception.RestoreFailedException(dataItem, errorMsg)
            }

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
                    Log.i(TAG, "APK安装耗时: ${endTime - startTime} ms")
                }
            } else {
                val installResult = appManager.installApks(apkFiles, userId)
                if (!installResult) {
                    throw InstallFailedException(dataItem, apkFiles.joinToString(", "))
                }
            }
            return true
        } finally {
            fs.delete(tempDir)
        }
    }

    private fun listApkFiles(fs: IFileSystem, dir: String, apkFiles: MutableList<String>) {
        val fileType = fs.fileType(dir)
        if (fileType == 1) {
            val files = fs.listDir(dir)
            for (file in files) {
                val fullPath = "$dir/$file"
                if (fullPath.endsWith(".apk")) {
                    apkFiles.add(fullPath)
                } else if (fs.fileType(fullPath) == 1) {
                    listApkFiles(fs, fullPath, apkFiles)
                }
            }
        }
    }
}

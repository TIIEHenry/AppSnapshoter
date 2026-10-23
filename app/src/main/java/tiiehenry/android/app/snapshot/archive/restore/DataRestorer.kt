package tiiehenry.android.app.snapshot.archive.restore

import android.util.Log
import kotlinx.coroutines.delay
import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.archive.bean.MetaDataItem
import tiiehenry.android.app.snapshot.main.launch.exception.MissingDataFileException
import tiiehenry.android.app.snapshot.main.launch.exception.MissingUidException
import tiiehenry.android.snapshot.app.IAppManager
import tiiehenry.android.snapshot.file.ICompressCallback
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.fs.CompressState
import tiiehenry.android.snapshot.provider.root.SELinuxShell
import java.nio.file.Paths
import java.util.UUID
import tiiehenry.android.snapshot.fs.IFileType

/**
 * 数据恢复器 - 负责恢复应用数据目录和额外项目
 */
object DataRestorer {

    private const val TAG = "DataRestorer"

    suspend fun restoreData(
        fs: IFileSystem,
        appManager: IAppManager,
        archiveItem: ArchiveItem,
        dataItem: MetaDataItem,
        targetDir: String,
        callback: DataItemCallback
    ) {
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

        val pathContext: String
        SELinuxShell.getContext(path = targetDir).also { result ->
            pathContext = if (result.isSuccess) result.outString else ""
        }

        Log.i(TAG, "Original SELinux context: $pathContext.")
        val parentDir = fs.getParent(targetDir) ?: ""
        if (parentDir.isNotEmpty()) {
            fs.mkdirs(parentDir)
        }
        if (targetDir.endsWith("/" + archiveItem.metaInfo.userId)) {
            throw IllegalArgumentException("！！！！！！！！！！！！ wrong dir: $targetDir")
        }

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

        val decompressor = fs.compressor
        val algorithm = dataItem.algorithm.ifEmpty { decompressor.detectAlgorithm(dataFile) }
        val task = decompressor.decompress(algorithm, dataFile, targetDir, compressCallback)
        task.start()

        var state = task.state()
        while (state == CompressState.COMPRESS_STATE_RUNNING || state == CompressState.COMPRESS_STATE_NONE) {
            delay(100)
            state = task.state()
        }
        var isSuccess = state == CompressState.COMPRESS_STATE_COMPLETE

        if (!isSuccess) {
            throw tiiehenry.android.app.snapshot.main.launch.exception.RestoreFailedException(dataItem, errorMsg)
        } else {
            var gid: UInt = uid.toUInt()
            if (dataItem.name == tiiehenry.android.app.snapshot.config.CompressItems.COMPRESS_ITEM_DATA ||
                dataItem.name == tiiehenry.android.app.snapshot.config.CompressItems.COMPRESS_ITEM_OBB ||
                dataItem.name == tiiehenry.android.app.snapshot.config.CompressItems.COMPRESS_ITEM_MEDIA
            ) {
                val pathGid = fs.getGid(targetDir)
                gid = pathGid.toUInt()
            }
            val out = mutableListOf<String>()
            SELinuxShell.chown(uid = uid.toUInt(), gid = gid, path = targetDir).also { result ->
                isSuccess = isSuccess && result.isSuccess
                out.addAll(result.out)
            }
            if (pathContext.isNotEmpty()) {
                SELinuxShell.chcon(context = pathContext, path = targetDir).also { result ->
                    isSuccess = isSuccess && result.isSuccess
                    out.addAll(result.out)
                }
            }
            Log.i(TAG, "Restore SELinux context: ${out.joinToString(", ")}")
            if (!isSuccess) {
                throw tiiehenry.android.app.snapshot.main.launch.exception.RestoreFailedException(dataItem, out.joinToString(", "))
            }
        }
    }

    suspend fun restoreExtraItem(
        fs: IFileSystem,
        archiveItem: ArchiveItem,
        dataItem: MetaDataItem,
        targetPath: String,
        callback: DataItemCallback
    ) {
        val archiveDir = archiveItem.path
        val dataFile = Paths.get(archiveDir, dataItem.file).toString()

        if (!fs.exists(dataFile)) {
            throw MissingDataFileException(dataItem, dataFile)
        }

        val pathContext: String
        SELinuxShell.getContext(path = targetPath).also { result ->
            pathContext = if (result.isSuccess) result.outString else ""
        }

        Log.i(TAG, "额外项目原始 SELinux context: $pathContext.")

        val parentDir = fs.getParent(targetPath) ?: ""
        if (parentDir.isNotEmpty()) {
            fs.mkdirs(parentDir)
        }

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

        val decompressor = fs.compressor
        val algorithm = dataItem.algorithm.ifEmpty { decompressor.detectAlgorithm(dataFile) }
        val staging = if (dataItem.isSourceFile) {
            Paths.get(parentDir, ".snapshot-restore-${UUID.randomUUID()}").toString().also {
                if (!fs.mkdirs(it)) throw IllegalStateException(it)
            }
        } else null
        var isSuccess: Boolean
        try {
            val task = decompressor.decompress(algorithm, dataFile, staging ?: targetPath, compressCallback)
            task.start()
            var state = task.state()
            while (state == CompressState.COMPRESS_STATE_RUNNING || state == CompressState.COMPRESS_STATE_NONE) {
                delay(100)
                state = task.state()
            }
            isSuccess = state == CompressState.COMPRESS_STATE_COMPLETE
            if (isSuccess && staging != null) {
                val expectedName = Paths.get(targetPath).fileName.toString()
                val children = fs.listDir(staging)
                val restoredFile = Paths.get(staging, expectedName).toString()
                if (children != listOf(expectedName) || fs.fileType(restoredFile) != IFileType.TYPE_FILE ||
                    fs.fileType(targetPath) == IFileType.TYPE_DIR) {
                    throw IllegalStateException(targetPath)
                }
                replaceRestoredFile(
                    restoredFile, targetPath,
                    Paths.get(parentDir, ".snapshot-original-${UUID.randomUUID()}").toString(),
                    fs::exists, fs::move, fs::delete
                )
            }
        } finally {
            if (staging != null) fs.delete(staging)
        }

        if (!isSuccess) {
            throw tiiehenry.android.app.snapshot.main.launch.exception.RestoreFailedException(dataItem, errorMsg)
        } else {
            val out = mutableListOf<String>()
            if (pathContext.isNotEmpty()) {
                SELinuxShell.chcon(context = pathContext, path = targetPath).also { result ->
                    isSuccess = isSuccess && result.isSuccess
                    out.addAll(result.out)
                }
            }
            Log.i(TAG, "恢复额外项目 SELinux context: ${out.joinToString(", ")}")
            if (!isSuccess) {
                throw tiiehenry.android.app.snapshot.main.launch.exception.RestoreFailedException(dataItem, out.joinToString(", "))
            }
        }
    }
}

/** Replace only the requested file; preserve the previous file if publishing fails. */
internal fun replaceRestoredFile(
    restored: String,
    target: String,
    original: String,
    exists: (String) -> Boolean,
    move: (String, String) -> Boolean,
    delete: (String) -> Boolean
) {
    val hadOriginal = exists(target)
    if (hadOriginal && !move(target, original)) throw IllegalStateException(target)
    try {
        if (!move(restored, target)) throw IllegalStateException(target)
    } catch (e: Exception) {
        if (hadOriginal && !move(original, target)) {
            e.addSuppressed(IllegalStateException(original))
        }
        throw e
    }
    if (hadOriginal) delete(original)
}

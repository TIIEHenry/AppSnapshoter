package tiiehenry.android.snapshot.provider.filesystem.compressors.tar

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import tiiehenry.android.snapshot.file.ICompressCallback
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.fs.CompressState
import tiiehenry.android.snapshot.provider.appmanager.util.LogHelper
import tiiehenry.android.snapshot.provider.filesystem.CompressResult
import tiiehenry.android.snapshot.provider.filesystem.IAlgorithmCompressor
import tiiehenry.android.snapshot.provider.filesystem.TaskCancellation
import tiiehenry.android.snapshot.provider.filesystem.finishCompress
import tiiehenry.android.snapshot.provider.filesystem.transferTarFifo
import tiiehenry.android.snapshot.provider.utils.normalizeTarStdErr
import tiiehenry.android.snapshot.task.ITaskHandler
import java.io.IOException

/**
 * 所有File文件操作都需要通过IFileSystem
 */
object TarCompressor : IAlgorithmCompressor {
    private const val TAG = "TarCompressor"

    /**
     * 将压缩回调的完成/失败同步到 [ITaskHandler] 状态，供 SnapshotCreator 中止判定。
     */
    private fun wrapStatefulCallback(
        callback: ICompressCallback,
        setState: (Int) -> Unit
    ): ICompressCallback {
        return object : ICompressCallback.Stub() {
            override fun onStart() {
                callback.onStart()
            }

            override fun onProgress(bytesWritten: Long, bytesPerS: Long) {
                callback.onProgress(bytesWritten, bytesPerS)
            }

            override fun onDone(originSize: Long, targetSize: Long, md5: String) {
                setState(CompressState.COMPRESS_STATE_COMPLETE)
                callback.onDone(originSize, targetSize, md5)
            }

            override fun onError(msg: String?) {
                setState(CompressState.COMPRESS_STATE_ERROR)
                callback.onError(msg)
            }
        }
    }

    override fun compress(
        context: Context,
        fileSystem: IFileSystem,
        dir: String,
        targetFile: String,
        excludes: List<String>,
        excludeFiles: List<String>,
        compressLevel: Int,
        callback: ICompressCallback
    ): ITaskHandler {
        Log.i(TAG, "start compress $dir to $targetFile")
        return object : ITaskHandler.Stub() {
            var state = CompressState.COMPRESS_STATE_NONE
            val cancellation = TaskCancellation()

            override fun id(): String {
                return "tar:" + dir + ">" + targetFile
            }

            override fun state(): Int {
                return state
            }

            override fun start() {
                state = CompressState.COMPRESS_STATE_RUNNING
                doCompress(
                    context,
                    fileSystem,
                    dir,
                    targetFile,
                    excludes,
                    excludeFiles,
                    cancellation,
                    wrapStatefulCallback(callback) { state = it }
                )
            }

            override fun cancel() {
                cancellation.cancel()
            }
        }
    }

    private fun doCompress(
        context: Context,
        fileSystem: IFileSystem,
        dir: String,
        targetFile: String,
        excludes: List<String>,
        excludeFiles: List<String>,
        cancellation: TaskCancellation,
        callback: ICompressCallback
    ) {
        if (!fileSystem.exists(dir)) {
            callback.onError("source not exists")
            return
        }
        if (fileSystem.exists(targetFile)) {
            callback.onError("target exists")
            return
        }
        callback.onStart()
        fileSystem.mkdirs(fileSystem.getParent(targetFile) ?: "")
        val originalSize = fileSystem.calculateSize(dir)
        runBlocking {
            streamToTarget(context, fileSystem, targetFile, originalSize, cancellation, callback) { stderr, relay ->
                fileSystem.createTarArchive(dir, "-", excludes, excludeFiles, stderr, relay)
            }
        }
    }

    override fun compressMultiple(
        context: Context,
        fileSystem: IFileSystem,
        files: List<String>,
        targetFile: String,
        compressLevel: Int,
        callback: ICompressCallback
    ): ITaskHandler {
        Log.i(TAG, "start compress multiple files to $targetFile")
        return object : ITaskHandler.Stub() {
            var state = CompressState.COMPRESS_STATE_NONE
            val cancellation = TaskCancellation()

            override fun id(): String {
                return "tar:multiple>" + targetFile
            }

            override fun state(): Int {
                return state
            }

            override fun start() {
                state = CompressState.COMPRESS_STATE_RUNNING
                doCompressMultiple(
                    context,
                    fileSystem,
                    files,
                    targetFile,
                    cancellation,
                    wrapStatefulCallback(callback) { state = it }
                )
            }

            override fun cancel() {
                cancellation.cancel()
            }
        }
    }

    private fun doCompressMultiple(
        context: Context,
        fileSystem: IFileSystem,
        files: List<String>,
        targetFile: String,
        cancellation: TaskCancellation,
        callback: ICompressCallback
    ) {
        if (files.isEmpty()) {
            callback.onError("no files to compress")
            return
        }
        files.forEach { filePath ->
            if (!fileSystem.exists(filePath)) {
                callback.onError("source not exists: $filePath")
                return
            }
        }
        if (fileSystem.exists(targetFile)) {
            callback.onError("target exists")
            return
        }
        callback.onStart()
        fileSystem.mkdirs(fileSystem.getParent(targetFile) ?: "")
        var originalSize = 0L
        files.forEach { filePath -> originalSize += fileSystem.calculateSize(filePath) }
        runBlocking {
            streamToTarget(context, fileSystem, targetFile, originalSize, cancellation, callback) { stderr, relay ->
                fileSystem.createTarArchiveForMultiple(files, "-", stderr, relay)
            }
        }
    }

    private suspend fun streamToTarget(
        context: Context,
        fileSystem: IFileSystem,
        targetFile: String,
        originalSize: Long,
        cancellation: TaskCancellation,
        callback: ICompressCallback,
        startTar: (String, String) -> Unit
    ) {
        try {
            val transferred = transferTarFifo(
                fileSystem,
                cancellation,
                reportError = { callback.onError(it) },
                startTar = { stderr, relay ->
                    try {
                        startTar(stderr, relay)
                    } catch (exception: Exception) {
                        Log.e(TAG, "Tar packaging failed: ${exception.message}", exception)
                        throw exception
                    }
                },
                readStderr = { input ->
                    try {
                        input.bufferedReader().use { reader ->
                            Log.i(
                                TAG,
                                "stdErr: ${normalizeTarStdErr(context.packageName, reader.readText())}"
                            )
                        }
                    } catch (error: Exception) {
                        LogHelper.e(TAG, "packageAndCompress#getStdErr", "Failed to get std err.", error)
                        throw error
                    }
                },
                consumeRelay = { input ->
                    val output = fileSystem.openOutputStream(targetFile)
                        ?: throw IOException("Failed to open $targetFile")
                    cancellation.track { output.close() }
                    ParcelFileDescriptor.AutoCloseOutputStream(output).use { out ->
                        input.copyTo(out)
                    }
                }
            )
            if (!transferred) return
            when (finishCompress(cancellation.cancelled.get(), fileSystem.exists(targetFile))) {
                CompressResult.DONE -> {
                    val compressedSize = fileSystem.length(targetFile)
                    val md5 = fileSystem.md5(targetFile)
                    callback.onDone(originalSize, compressedSize, md5)
                }
                CompressResult.CANCELLED -> throw CancellationException("Compression cancelled")
                CompressResult.MISSING -> callback.onError(
                    "Compression completed but target file not found"
                )
            }
        } catch (error: Exception) {
            if (error is CancellationException || cancellation.cancelled.get()) {
                callback.onError("Compression cancelled")
            } else {
                callback.onError(error.message ?: "Compression failed")
            }
            runCatching { fileSystem.delete(targetFile) }
        }
    }
}

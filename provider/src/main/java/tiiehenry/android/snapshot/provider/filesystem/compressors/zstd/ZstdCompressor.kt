package tiiehenry.android.snapshot.provider.filesystem.compressors.zstd

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import com.github.luben.zstd.Zstd
import com.github.luben.zstd.ZstdOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import nota.io.FlowableStreamParallelCopier
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
import java.io.InputStream

/**
 * 所有File文件操作都需要通过IFileSystem
 */
object ZstdCompressor : IAlgorithmCompressor {
    private const val TAG = "ZstdCompressor"

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
        Log.i(TAG, "start compress $dir to $targetFile, level: $compressLevel")
        return object : ITaskHandler.Stub() {
            var state = CompressState.COMPRESS_STATE_NONE
            val cancellation = TaskCancellation()

            override fun id(): String {
                return "zstd:" + dir + ">" + targetFile
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
                    compressLevel,
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
        compressLevel: Int,
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
            streamToTarget(
                context, fileSystem, targetFile, originalSize, compressLevel, cancellation, callback
            ) { stderr, relay ->
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
        Log.i(TAG, "start compress multiple files to $targetFile, level: $compressLevel")
        return object : ITaskHandler.Stub() {
            var state = CompressState.COMPRESS_STATE_NONE
            val cancellation = TaskCancellation()

            override fun id(): String {
                return "zstd:multiple>" + targetFile
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
                    compressLevel,
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
        compressLevel: Int,
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
            streamToTarget(
                context, fileSystem, targetFile, originalSize, compressLevel, cancellation, callback
            ) { stderr, relay ->
                fileSystem.createTarArchiveForMultiple(files, "-", stderr, relay)
            }
        }
    }

    private suspend fun streamToTarget(
        context: Context,
        fileSystem: IFileSystem,
        targetFile: String,
        originalSize: Long,
        compressLevel: Int,
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
                    compressTarStream(
                        fileSystem, input, targetFile, compressLevel, callback, cancellation
                    )
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

    /**
     * 流式压缩 tar 文件
     */
    private suspend fun compressTarStream(
        fileSystem: IFileSystem,
        tarInput: InputStream,
        targetFile: String,
        compressLevel: Int,
        callback: ICompressCallback,
        cancellation: TaskCancellation
    ) {
        val outputStream = fileSystem.openOutputStream(targetFile)
            ?: throw IOException("Failed to open $targetFile")
        cancellation.track { runCatching { outputStream.close() } }
        outputStream.use { parcelOutput ->
            val fos = ParcelFileDescriptor.AutoCloseOutputStream(parcelOutput)
            ZstdOutputStream(fos).use { zstdOutputStream ->
                zstdOutputStream.setWorkers(Runtime.getRuntime().availableProcessors())
                zstdOutputStream.setLevel(mapToZstdLevel(compressLevel))
                val transformer = FlowableStreamParallelCopier(tarInput, zstdOutputStream)
                cancellation.track { transformer.cancel() }
                val progressJob = Job()
                try {
                    CoroutineScope(Dispatchers.Default + progressJob).launch {
                        transformer.progressFlow.collectLatest { progress ->
                            callback.onProgress(progress.bytesWritten, progress.speed)
                        }
                    }
                    transformer.startAndWait()
                } finally {
                    progressJob.cancel()
                }
            }
        }
    }

    /**
     * 将用户友好的压缩级别 (1-9) 映射到 Zstd 实际压缩级别
     * Zstd 压缩级别范围通常是 1-22，数字越大压缩率越高但速度越慢
     * 这里将 1-9 线性映射到 1-22 范围
     *
     *         //--fast / 1	非常快	最低	低	实时数据流、网络传输、CPU 瓶颈高于 IO
     *         //2 - 5	很快	较低	低	快速备份、常用文件传输
     *         //6 - 15	中等	中等	中等	通用推荐 (速度与压缩率平衡)
     *         //16 - 19	较慢	较高	中等	长期存储、软件分发、对存储空间敏感
     *         //--ultra 20 - 22	非常慢	最高	高	一次性归档、极度追求最小文件大小
     */
    private fun mapToZstdLevel(userLevel: Int): Int {
        // 限制用户级别在 1-9 范围内
//        val clampedLevel = userLevel.coerceIn(1, 9)
        // 公式：zstdLevel = 1 + (clampedLevel - 1) * (22 - 1) / (9 - 1)
        // 线性映射：1->1, 9->22
//        val zstdLevel = 1 + (clampedLevel - 1) * 21 / 8
        val minimumValue = 1
        val maximumValue = Zstd.maxCompressionLevel()//22
        //tar 8004ms
        //1 1.96g->1.88g 10019ms 10392ms
        //3 1.96g->1.86g 10825ms 10489ms 10979ms
        //5 1.96g->1.86g 10655ms 10809ms 10712ms
        //7 1.96g->1.86g 332298ms

        //这里使用非线性映射
        val zstdLevel=when(userLevel){
            1->1
            3->3
            5->8
            7->15
            9->19
            else -> 2
        }
        Log.i(TAG, "Mapping user level $userLevel to Zstd level $zstdLevel (min: $minimumValue, max: $maximumValue)")
        return zstdLevel.coerceIn(minimumValue, maximumValue)
    }
}

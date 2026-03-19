package tiiehenry.android.snapshot.provider.filesystem.compressors.zstd

import android.os.ParcelFileDescriptor
import android.util.Log
import com.github.luben.zstd.ZstdInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import tiiehenry.android.snapshot.file.ICompressCallback
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.fs.CompressState
import tiiehenry.android.snapshot.task.ITaskHandler
import java.util.concurrent.atomic.AtomicBoolean

object ZstdDecompressor {
    private const val TAG = "ZstdDecompressor"

    fun decompress(
        fileSystem: IFileSystem,
        file: String,
        targetDir: String,
        callback: ICompressCallback?
    ): ITaskHandler {
        var currentState = CompressState.COMPRESS_STATE_NONE
        val isCancel = AtomicBoolean(false)

        return object : ITaskHandler.Stub() {
            override fun id(): String {
                return "zstd-decompress:$file"
            }

            override fun state(): Int {
                return currentState
            }

            override fun start() {
                currentState = CompressState.COMPRESS_STATE_RUNNING
                doDecompress(fileSystem, file, targetDir, callback, isCancel) { newState ->
                    currentState = newState
                }
            }

            override fun cancel() {
                isCancel.set(true)
            }
        }
    }

    private fun doDecompress(
        fileSystem: IFileSystem,
        file: String,
        targetDir: String,
        callback: ICompressCallback?,
        isCancel: AtomicBoolean,
        updateState: (Int) -> Unit
    ) {
        if (!fileSystem.exists(file)) {
            callback?.onError("Source file not exists: $file")
            Log.e(TAG, "Decompression error: Source file not exists: $file")
            updateState(CompressState.COMPRESS_STATE_ERROR)
            return
        }

        callback?.onStart()

        // 创建临时FIFO管道用于流式处理
        val tempZstdFifo = fileSystem.createTempFile("fifo-", ".fifo")

        try {
            // 使用FIFO管道进行流式解压缩
            runBlocking {
                streamDecompress(
                    fileSystem, file, targetDir, tempZstdFifo,
                    callback, isCancel
                ) { newState ->
                    updateState(newState)
                }
            }
            updateState(CompressState.COMPRESS_STATE_COMPLETE)
        } catch (e: CancellationException) {
            callback?.onError("Decompression cancelled")
            Log.e(TAG, "Decompression error: Decompression cancelled", e)
            updateState(CompressState.COMPRESS_STATE_ERROR)
        } catch (e: Exception) {
            Log.e(TAG, "Decompress error", e)
            callback?.onError(e.message ?: "Unknown error")
            updateState(CompressState.COMPRESS_STATE_ERROR)
        } finally {
            // 清理临时FIFO管道
            runCatching {
                fileSystem.delete(tempZstdFifo)
            }
        }
    }

    private suspend fun streamDecompress(
        fileSystem: IFileSystem,
        sourceFile: String,
        targetDir: String,
        zstdFifo: String,
        callback: ICompressCallback?,
        isCancel: AtomicBoolean,
        updateState: (Int) -> Unit
    ) {
        Log.i(TAG, "Starting stream decompress: $sourceFile to $targetDir")
        // 确保目标目录存在
        fileSystem.mkdirs(targetDir)
        // Clean directory before decompression
        fileSystem.cleanDir(targetDir) // Clean directory before decompression

        fileSystem.delete(zstdFifo)
        // 创建FIFO管道
        if (!fileSystem.mkfifo(zstdFifo, 420)) {
            callback?.onError("Failed to create zstd fifo")
            return
        }

        val sourceSize = fileSystem.length(sourceFile)

        try {
            coroutineScope {
                // 步骤2: 解包tar文件
                val extractJob = async(Dispatchers.IO) {
                    runCatching {
                        extractTarFromFifo(fileSystem, zstdFifo, targetDir, callback, isCancel)
                    }.onFailure { exception ->
                        Log.e(TAG, "Tar extraction failed", exception)
                        throw exception
                    }
                }

                // 步骤1: 解压zstd文件到FIFO管道
                val decompressJob = async(Dispatchers.IO) {
                    runCatching {
                        decompressZstdToFifo(fileSystem, sourceFile, zstdFifo, callback, isCancel)
                    }.onFailure { exception ->
                        Log.e(TAG, "Zstd decompression failed", exception)
                        throw exception
                    }
                }

                // 等待所有任务完成
                decompressJob.await()
                extractJob.await()
            }

            // 成功完成
            val targetSize = fileSystem.calculateSize(targetDir)
            callback?.onDone(sourceSize, targetSize, "")
            updateState(CompressState.COMPRESS_STATE_COMPLETE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        }
    }

    private fun decompressZstdToFifo(
        fileSystem: IFileSystem,
        sourceFile: String,
        fifoPath: String,
        callback: ICompressCallback?,
        isCancel: AtomicBoolean
    ) {
        Log.i(TAG, "Starting zstd decompress: $sourceFile to $fifoPath")
        try {
            val inPfd = fileSystem.openInputStream(sourceFile)
            val outPfd = fileSystem.openOutputStream(fifoPath)

            if (inPfd == null || outPfd == null) {
                throw Exception("Failed to open input or output stream")
            }

            var totalBytes = 0L

            ParcelFileDescriptor.AutoCloseInputStream(inPfd).use { fileInput ->
                ZstdInputStream(fileInput).use { decompressedInput ->
                    ParcelFileDescriptor.AutoCloseOutputStream(outPfd).use { fileOutput ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (decompressedInput.read(buffer).also { bytesRead = it } != -1) {
                            if (isCancel.get()) {
                                return
                            }
                            fileOutput.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead
                            callback?.onProgress(totalBytes, 0)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Zstd decompress error", e)
            throw e
        }
    }

    private fun copyFifo(
        fileSystem: IFileSystem,
        inputFifo: String,
        outputFifo: String,
        callback: ICompressCallback?,
        isCancel: AtomicBoolean
    ) {
        try {
            val inputStream = fileSystem.openInputStream(inputFifo)
            val outputStream = fileSystem.openOutputStream(outputFifo)

            if (inputStream != null && outputStream != null) {
                ParcelFileDescriptor.AutoCloseInputStream(inputStream).use { input ->
                    ParcelFileDescriptor.AutoCloseOutputStream(outputStream).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (isCancel.get()) {
                                return
                            }
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fifo copy error", e)
            throw e
        }
    }

    private fun extractTarFromFifo(
        fileSystem: IFileSystem,
        tarFifo: String,
        targetDir: String,
        callback: ICompressCallback?,
        isCancel: AtomicBoolean
    ) {
        Log.i(TAG, "Starting tar extract: $tarFifo to $targetDir")
        try {
            // Use IFileSystem implementation with ZstdInputStream in IFileSystemRootService
            val success = fileSystem.extractTar(tarFifo, targetDir)
            if (!success) {
                throw Exception("Tar extraction failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tar extract error", e)
            throw e
        }
    }
}

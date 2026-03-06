package tiiehenry.android.snapshotor.provider.filesystem.compressors.tar

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import tiiehenry.android.snapshotor.file.ICompressCallback
import tiiehenry.android.snapshotor.file.IFileSystem
import tiiehenry.android.snapshotor.fs.CompressState
import tiiehenry.android.snapshotor.task.ITaskHandler
import java.util.concurrent.atomic.AtomicBoolean

object TarDecompressor {
    private const val TAG = "TarDecompressor"

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
                return "tar-decompress:$file"
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
            updateState(CompressState.COMPRESS_STATE_ERROR)
            return
        }

        callback?.onStart()

        try {
            // 使用FIFO管道进行流式解压缩
            runBlocking {
                streamDecompress(
                    fileSystem, file, targetDir,
                    callback, isCancel
                ) { newState ->
                    updateState(newState)
                }
            }
            updateState(CompressState.COMPRESS_STATE_COMPLETE)
        } catch (e: CancellationException) {
            callback?.onError("Decompression cancelled")
            updateState(CompressState.COMPRESS_STATE_ERROR)
        } catch (e: Exception) {
            Log.e(TAG, "Decompress error", e)
            callback?.onError(e.message ?: "Unknown error")
            updateState(CompressState.COMPRESS_STATE_ERROR)
        }
    }

    private suspend fun streamDecompress(
        fileSystem: IFileSystem,
        sourceFile: String,
        targetDir: String,
        callback: ICompressCallback?,
        isCancel: AtomicBoolean,
        updateState: (Int) -> Unit
    ) {
        Log.i(TAG, "Starting stream decompress: $sourceFile to $targetDir")
        // 确保目标目录存在
        fileSystem.mkdirs(targetDir)
        // Clean directory before decompression
        fileSystem.cleanDir(targetDir) // Clean directory before decompression
        val sourceSize = fileSystem.length(sourceFile)

        try {
            coroutineScope {
                // 步骤2: 解包tar文件
                val extractJob = async(Dispatchers.IO) {
                    runCatching {
                        extractTar(fileSystem, sourceFile, targetDir, callback, isCancel)
                    }.onFailure { exception ->
                        Log.e(TAG, "Tar extraction failed", exception)
                        throw exception
                    }
                }
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

    private fun extractTar(
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

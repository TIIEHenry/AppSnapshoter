package tiiehenry.android.snapshot.provider.filesystem.compressors.tar

import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import tiiehenry.android.snapshot.file.ICompressCallback
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.fs.CompressState
import tiiehenry.android.snapshot.provider.filesystem.ReliablePipeTransfer
import tiiehenry.android.snapshot.provider.filesystem.TaskCancellation
import tiiehenry.android.snapshot.provider.filesystem.extractScratch
import tiiehenry.android.snapshot.provider.filesystem.publishExtractedDirectory
import tiiehenry.android.snapshot.provider.filesystem.shouldCommitExtract
import tiiehenry.android.snapshot.task.ITaskHandler
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.coroutineContext

object TarDecompressor {
    private const val TAG = "TarDecompressor"

    fun decompress(
        fileSystem: IFileSystem,
        file: String,
        targetDir: String,
        callback: ICompressCallback?
    ): ITaskHandler {
        var currentState = CompressState.COMPRESS_STATE_NONE
        val cancellation = TaskCancellation()

        return object : ITaskHandler.Stub() {
            override fun id(): String {
                return "tar-decompress:$file"
            }

            override fun state(): Int {
                return currentState
            }

            override fun start() {
                currentState = CompressState.COMPRESS_STATE_RUNNING
                doDecompress(fileSystem, file, targetDir, callback, cancellation) { newState ->
                    currentState = newState
                }
            }

            override fun cancel() {
                cancellation.cancel()
            }
        }
    }

    private fun doDecompress(
        fileSystem: IFileSystem,
        file: String,
        targetDir: String,
        callback: ICompressCallback?,
        cancellation: TaskCancellation,
        updateState: (Int) -> Unit
    ) {
        if (!fileSystem.exists(file)) {
            callback?.onError("Source file not exists: $file")
            Log.e(TAG, "Decompression error: Source file not exists: $file")
            updateState(CompressState.COMPRESS_STATE_ERROR)
            return
        }

        callback?.onStart()

        try {
            runBlocking {
                streamDecompress(fileSystem, file, targetDir, callback, cancellation, updateState)
            }
            updateState(CompressState.COMPRESS_STATE_COMPLETE)
        } catch (e: CancellationException) {
            callback?.onError("Decompression cancelled")
            Log.e(TAG, "Decompression error: Decompression cancelled", e)
            updateState(CompressState.COMPRESS_STATE_ERROR)
        } catch (e: Exception) {
            if (cancellation.cancelled.get()) {
                callback?.onError("Decompression cancelled")
                Log.e(TAG, "Decompression error: Decompression cancelled", e)
            } else {
                Log.e(TAG, "Decompress error", e)
                callback?.onError(e.message ?: "Unknown error")
            }
            updateState(CompressState.COMPRESS_STATE_ERROR)
        }
    }

    private suspend fun streamDecompress(
        fileSystem: IFileSystem,
        sourceFile: String,
        targetDir: String,
        callback: ICompressCallback?,
        cancellation: TaskCancellation,
        updateState: (Int) -> Unit
    ) {
        coroutineContext[Job]?.let(cancellation::bindJob)
        Log.i(TAG, "Starting stream decompress: $sourceFile to $targetDir")
        val scratch = extractScratch(targetDir, UUID.randomUUID().toString())
        var committed = false
        try {
            check(fileSystem.mkdirs(scratch.temp) || fileSystem.exists(scratch.temp)) {
                "Cannot create extraction directory: ${scratch.temp}"
            }
            val sourceSize = fileSystem.length(sourceFile)
            ReliablePipeTransfer().use { pipe ->
                cancellation.track { pipe.abort("Decompression cancelled") }
                cancellation.ensureActive()
                pipe.transfer(
                    produce = { output ->
                        val descriptor = fileSystem.openInputStream(sourceFile)
                            ?: throw IOException("Cannot open archive: $sourceFile")
                        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                            input.copyTo(output)
                        }
                    },
                    consume = { tarInput ->
                        extractTar(fileSystem, tarInput, scratch.temp, cancellation)
                    }
                )
            }
            if (!shouldCommitExtract(cancellation.cancelled.get(), extracted = true)) {
                throw CancellationException("Decompression cancelled")
            }
            if (!publishExtractedDirectory(
                    scratch.temp,
                    targetDir,
                    scratch.backup,
                    fileSystem::exists,
                    fileSystem::move,
                    fileSystem::delete
                )
            ) {
                throw IOException("Failed to replace $targetDir")
            }
            committed = true
            val targetSize = fileSystem.calculateSize(targetDir)
            callback?.onDone(sourceSize, targetSize, "")
            updateState(CompressState.COMPRESS_STATE_COMPLETE)
        } finally {
            if (!committed) runCatching { fileSystem.delete(scratch.temp) }
        }
    }

    private fun extractTar(
        fileSystem: IFileSystem,
        tarInput: ParcelFileDescriptor,
        targetDir: String,
        cancellation: TaskCancellation
    ) {
        Log.i(TAG, "Starting tar extract to $targetDir")
        val success = fileSystem.extractTarFromFd(tarInput, targetDir)
        if (cancellation.cancelled.get()) {
            throw CancellationException("Decompression cancelled")
        }
        if (!success) throw IOException("Tar extraction failed")
    }
}

package tiiehenry.android.snapshot.provider.filesystem.compressors.zstd

import android.os.ParcelFileDescriptor
import android.util.Log
import com.github.luben.zstd.ZstdInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import tiiehenry.android.snapshot.file.ICompressCallback
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.fs.CompressState
import tiiehenry.android.snapshot.fs.IFileType
import tiiehenry.android.snapshot.provider.filesystem.ReliablePipeTransfer
import tiiehenry.android.snapshot.provider.filesystem.extractScratch
import tiiehenry.android.snapshot.provider.filesystem.publishExtractedDirectory
import tiiehenry.android.snapshot.provider.filesystem.shouldCommitExtract
import tiiehenry.android.snapshot.task.ITaskHandler
import java.util.UUID
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

object ZstdDecompressor {
    private const val TAG = "ZstdDecompressor"

    fun decompress(
        fileSystem: IFileSystem,
        file: String,
        targetDir: String,
        callback: ICompressCallback
    ): ITaskHandler {
        val state = AtomicInteger(CompressState.COMPRESS_STATE_NONE)
        val cancelled = AtomicBoolean(false)
        val activePipe = AtomicReference<ReliablePipeTransfer?>()
        return object : ITaskHandler.Stub() {
            override fun id(): String = "zstd-decompress:$file"
            override fun state(): Int = state.get()

            override fun start() {
                if (!state.compareAndSet(CompressState.COMPRESS_STATE_NONE, CompressState.COMPRESS_STATE_RUNNING)) return
                val scratch = extractScratch(targetDir, UUID.randomUUID().toString())
                var committed = false
                try {
                    callback.onStart()
                    val sourceSize = fileSystem.length(file)
                    // Open the archive before touching the destination or starting
                    // tar. An unreadable source cannot leave a consumer waiting.
                    val descriptor = fileSystem.openInputStream(file)
                        ?: throw IOException("Cannot open archive: $file")
                    ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                        ZstdInputStream(input).use { decompressed ->
                            check(fileSystem.mkdirs(scratch.temp) || fileSystem.fileType(scratch.temp) == IFileType.TYPE_DIR) {
                                "Cannot create extraction directory: ${scratch.temp}"
                            }
                            ReliablePipeTransfer().use { pipe ->
                                activePipe.set(pipe)
                                if (cancelled.get()) throw CancellationException("Decompression cancelled")
                                runBlocking {
                                    pipe.transfer(
                                        produce = { output ->
                                            val buffer = ByteArray(128 * 1024)
                                            val started = System.nanoTime()
                                            var written = 0L
                                            var lastProgress = 0L
                                            while (true) {
                                                if (cancelled.get()) throw CancellationException("Decompression cancelled")
                                                val count = decompressed.read(buffer)
                                                if (count == -1) break
                                                output.write(buffer, 0, count)
                                                written += count
                                                val elapsed = (System.nanoTime() - started) / 1_000_000
                                                if (elapsed - lastProgress >= 100) {
                                                    callback.onProgress(written, written * 1000 / elapsed.coerceAtLeast(1))
                                                    lastProgress = elapsed
                                                }
                                            }
                                        },
                                        consume = { tarInput ->
                                            if (!fileSystem.extractTarFromFd(tarInput, scratch.temp)) {
                                                throw IOException("Tar extraction failed")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (!shouldCommitExtract(cancelled.get(), extracted = true)) {
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
                    state.set(CompressState.COMPRESS_STATE_COMPLETE)
                    callback.onDone(sourceSize, targetSize, "")
                } catch (error: Exception) {
                    state.set(CompressState.COMPRESS_STATE_ERROR)
                    Log.e(TAG, "Decompression failed", error)
                    callback.onError(error.message ?: "Decompression failed")
                } finally {
                    activePipe.getAndSet(null)?.close()
                    if (!committed) runCatching { fileSystem.delete(scratch.temp) }
                }
            }

            override fun cancel() {
                cancelled.set(true)
                activePipe.get()?.abort("Decompression cancelled")
            }
        }
    }
}

package tiiehenry.android.snapshotor.provider.filesystem.decompressors

import android.util.Log
import tiiehenry.android.snapshotor.file.ICompressCallback
import tiiehenry.android.snapshotor.file.IFileSystem
import tiiehenry.android.snapshotor.fs.CompressState
import tiiehenry.android.snapshotor.task.ITaskHandler
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.concurrent.atomic.AtomicBoolean

object ZipDecompressor {
    private const val TAG = "ZipDecompressor"

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
                return "zip-decompress:$file"
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
            // 确保目标目录存在
            fileSystem.mkdirs(targetDir)
            // Clean directory before decompression
            fileSystem.cleanDir(targetDir) // Clean directory before decompression

            val inputStream = fileSystem.openInputStream(file)
            if (inputStream == null) {
                callback?.onError("Failed to open input stream")
                updateState(CompressState.COMPRESS_STATE_ERROR)
                return
            }

            var totalBytes = 0L

            inputStream.use { parcelInput ->
                val fis = android.os.ParcelFileDescriptor.AutoCloseInputStream(parcelInput)
                ZipInputStream(fis).use { zipInput ->
                    var entry: ZipEntry? = zipInput.nextEntry
                    while (entry != null) {
                        if (isCancel.get()) {
                            callback?.onError("Decompression cancelled")
                            updateState(CompressState.COMPRESS_STATE_ERROR)
                            return
                        }

                        val targetPath = "$targetDir/${entry.name}"

                        if (entry.isDirectory) {
                            fileSystem.mkdirs(targetPath)
                        } else {
                            // 确保父目录存在
                            val parentDir = fileSystem.getParent(targetPath) ?: ""
                            if (parentDir.isNotEmpty()) {
                                fileSystem.mkdirs(parentDir)
                            }

                            val outputStream = fileSystem.openOutputStream(targetPath)
                            if (outputStream != null) {
                                outputStream.use { parcelOutput ->
                                    val fos = android.os.ParcelFileDescriptor.AutoCloseOutputStream(parcelOutput)
                                    val buffer = ByteArray(8192)
                                    var bytesRead: Int
                                    while (zipInput.read(buffer).also { bytesRead = it } != -1) {
                                        fos.write(buffer, 0, bytesRead)
                                        totalBytes += bytesRead
                                        callback?.onProgress(totalBytes, 0)
                                    }
                                }
                            }
                        }

                        zipInput.closeEntry()
                        entry = zipInput.nextEntry
                    }
                }
            }

            // 成功完成
            val targetSize = fileSystem.calculateSize(targetDir)
            val sourceSize = fileSystem.length(file)
            callback?.onDone(sourceSize, targetSize, "")
            updateState(CompressState.COMPRESS_STATE_COMPLETE)

        } catch (e: Exception) {
            Log.e(TAG, "Decompress error", e)
            callback?.onError(e.message ?: "Unknown error")
            updateState(CompressState.COMPRESS_STATE_ERROR)
        }
    }
}

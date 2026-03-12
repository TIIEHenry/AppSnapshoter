package tiiehenry.android.snapshot.provider.filesystem.compressors.zstd

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import com.github.luben.zstd.ZstdOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import tiiehenry.android.snapshot.file.ICompressCallback
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.fs.CompressState
import tiiehenry.android.snapshot.provider.filesystem.IAlgorithmCompressor
import tiiehenry.android.snapshot.task.ITaskHandler
import tiiehenry.android.snapshot.util.LogHelper
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 所有File文件操作都需要通过IFileSystem
 */
object ZstdCompressor : IAlgorithmCompressor {
    private const val TAG = "ZstdCompressor"

    override fun compress(
        context: Context,
        fileSystem: IFileSystem,
        dir: String,
        targetFile: String,
        excludes: List<String>,
        excludeFiles: List<String>,
        callback: ICompressCallback
    ): ITaskHandler {
        Log.i(TAG, "start compress $dir to $targetFile")
        return object : ITaskHandler.Stub() {
            var state = CompressState.COMPRESS_STATE_NONE
            var isCancel = AtomicBoolean(false)

            override fun id(): String {
                return "zstd:" + dir + ">" + targetFile
            }

            override fun state(): Int {
                return state
            }

            override fun start() {
                state = CompressState.COMPRESS_STATE_RUNNING
                doCompress(context, fileSystem, dir, targetFile, excludes, excludeFiles, callback)
            }

            override fun cancel() {
                isCancel.set(true)
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

        // 使用流式处理，但仍调用createTarArchive方法
        runBlocking {
            streamCompress(context, fileSystem, dir, targetFile, excludes, excludeFiles, callback)
        }
    }

    const val TMP_FIFO_PREFIX = "fifo-"
    const val TMP_SUFFIX = ".tmp"

    private fun normalizeTarStdErr(packageName: String, stderr: String): String {
        if (stderr.isBlank()) return stderr
        val prefixRegex = Regex("^${Regex.escape(packageName)}:root:\\d+:\\s*")
        return stderr.lineSequence()
            .joinToString(separator = "\n") { line -> line.replace(prefixRegex, "") }
    }

    /**
     * 流式压缩处理，使用createTarArchive但以流式方式进行
     */
    private suspend fun streamCompress(
        context: Context,
        fileSystem: IFileSystem,
        sourceDir: String,
        targetFile: String,
        excludes: List<String>,
        excludeFiles: List<String>,
        callback: ICompressCallback
    ) {
        var errorMessage = ""

        var status = 0
        var info = ""

        val stdOut = fileSystem.createTempFile(TMP_FIFO_PREFIX, TMP_SUFFIX)
        fileSystem.delete(stdOut)
        val stdErr = fileSystem.createTempFile(TMP_FIFO_PREFIX, TMP_SUFFIX)
        fileSystem.delete(stdErr)

        // 使用fileSystem接口创建FIFO管道
        if (!fileSystem.mkfifo(stdErr, 420)) {
            callback.onError("Failed to create stderr FIFO")
            return
        }
        if (!fileSystem.mkfifo(stdOut, 420)) {
            callback.onError("Failed to create stdout FIFO")
            return
        }

        // 原始文件夹大小
        val originalSize = fileSystem.calculateSize(sourceDir)
        try {
            coroutineScope {
                // 并发执行tar打包和Zstd压缩
                val tarPackaging = async(Dispatchers.IO) {
                    runCatching {
                        // 使用现有的createTarArchive方法进行打包
                        fileSystem.createTarArchive(
                            sourceDir,
                            "-",
                            excludes,
                            excludeFiles,
                            stdErr,
                            stdOut
                        )
                    }.onFailure { exception ->
                        errorMessage = "Tar packaging failed: ${exception.message}"
                        Log.e(TAG, errorMessage, exception)
                        throw exception
                    }

                }
                val getStdErr = async(Dispatchers.IO) {
                    runCatching {
                        FileInputStream(stdErr).use { fileInputStream ->
                            fileInputStream.bufferedReader().use { bufferedReader ->
                                info = normalizeTarStdErr(
                                    context.packageName,
                                    bufferedReader.readText()
                                )
                            }
                        }
                    }.onFailure {
                        val msg = "Failed to get std err."
                        LogHelper.e(TAG, "packageAndCompress#getStdErr", msg, it)
                        status = -1
                        info = msg
                        throw IllegalStateException()
                    }
                }


                val zstdCompression = async(Dispatchers.IO) {
                    runCatching {
                        compressTarStream(fileSystem, stdOut, targetFile, callback)
                    }.onFailure { exception ->
                        errorMessage = "Zstd compression failed: ${exception.message}"
                        Log.e(TAG, errorMessage, exception)
                        throw exception
                    }
                }

                // 等待两个任务都完成
                tarPackaging.await()
                getStdErr.await()
                Log.i(TAG, "stdErr: $info")
                zstdCompression.await()
            }

            // 成功完成，计算最终信息
            if (fileSystem.exists(targetFile)) {
                val compressedSize = fileSystem.length(targetFile)
                val md5 = fileSystem.md5(targetFile)
                callback.onDone(originalSize, compressedSize, md5)
            } else {
                callback.onError(errorMessage.ifEmpty { "Compression completed but target file not found" })
            }

        } catch (e: CancellationException) {
            callback.onError("Compression cancelled")
            // 清理部分完成的文件
            runCatching { fileSystem.delete(targetFile) }
        } catch (e: Exception) {
            callback.onError(e.message ?: "Compression failed")
            // 清理失败的文件
            runCatching { fileSystem.delete(targetFile) }
        } finally {
            // 清理临时tar文件
            runCatching {
                fileSystem.delete(stdOut)
                fileSystem.delete(stdErr)
            }
        }
    }

    /**
     * 流式压缩tar文件
     */
    private suspend fun compressTarStream(
        fileSystem: IFileSystem,
        tarFile: String,
        targetFile: String,
        callback: ICompressCallback
    ) {
        val inputStream = fileSystem.openInputStream(tarFile)
        val outputStream = fileSystem.openOutputStream(targetFile)

        if (inputStream != null && outputStream != null) {
            inputStream.use { parcelInput ->
                val fis = ParcelFileDescriptor.AutoCloseInputStream(parcelInput)
                outputStream.use { parcelOutput ->
                    val fos = ParcelFileDescriptor.AutoCloseOutputStream(parcelOutput)
                    CountingOutputStream(
                        source = fos,
                        onProgress = { bytesWritten, speed ->
                            callback.onProgress(bytesWritten, speed)
                        }
                    ).use { countingOutputStream ->
                        ZstdOutputStream(countingOutputStream).use { zstdOutputStream ->
                            zstdOutputStream.setWorkers(Runtime.getRuntime().availableProcessors())
                            fis.copyTo(zstdOutputStream)
                        }
                    }
                }
            }
        } else {
            throw Exception("Failed to open input or output stream for compression")
        }
    }

    override fun compressMultiple(
        context: Context,
        fileSystem: IFileSystem,
        files: List<String>,
        targetFile: String,
        callback: ICompressCallback
    ): ITaskHandler {
        Log.i(TAG, "start compress multiple files to $targetFile")
        return object : ITaskHandler.Stub() {
            var state = CompressState.COMPRESS_STATE_NONE
            var isCancel = AtomicBoolean(false)

            override fun id(): String {
                return "zstd:multiple>" + targetFile
            }

            override fun state(): Int {
                return state
            }

            override fun start() {
                state = CompressState.COMPRESS_STATE_RUNNING
                doCompressMultiple(
                    context, fileSystem, files, targetFile, callback
                )
            }

            override fun cancel() {
                isCancel.set(true)
            }
        }
    }

    private fun doCompressMultiple(
        context: Context,
        fileSystem: IFileSystem,
        files: List<String>,
        targetFile: String,
        callback: ICompressCallback
    ) {
        if (files.isEmpty()) {
            callback.onError("no files to compress")
            return
        }

        // 验证所有文件存在
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

        runBlocking {
            streamCompressMultiple(
                context,
                fileSystem,
                files,
                targetFile,
                callback
            )
        }
    }

    /**
     * 流式压缩多个文件
     */
    private suspend fun streamCompressMultiple(
        context: Context,
        fileSystem: IFileSystem,
        files: List<String>,
        targetFile: String,
        callback: ICompressCallback
    ) {
        var errorMessage = ""
        var status = 0
        var info = ""

        val stdOut = fileSystem.createTempFile(TMP_FIFO_PREFIX, TMP_SUFFIX)
        fileSystem.delete(stdOut)
        val stdErr = fileSystem.createTempFile(TMP_FIFO_PREFIX, TMP_SUFFIX)
        fileSystem.delete(stdErr)

        if (!fileSystem.mkfifo(stdErr, 420)) {
            callback.onError("Failed to create stderr FIFO")
            return
        }
        if (!fileSystem.mkfifo(stdOut, 420)) {
            callback.onError("Failed to create stdout FIFO")
            return
        }

        // 计算总大小
        var originalSize = 0L
        files.forEach { filePath ->
            originalSize += fileSystem.calculateSize(filePath)
        }

        try {
            coroutineScope {
                // 并发执行tar打包和Zstd压缩
                val tarPackaging = async(Dispatchers.IO) {
                    runCatching {
                        // 使用createTarArchiveForMultiple方法打包所有文件
                        fileSystem.createTarArchiveForMultiple(
                            files,
                            "-",
                            stdErr,
                            stdOut
                        )
                    }.onFailure { exception ->
                        errorMessage = "Tar packaging failed: ${exception.message}"
                        Log.e(TAG, errorMessage, exception)
                        throw exception
                    }
                }

                val getStdErr = async(Dispatchers.IO) {
                    runCatching {
                        FileInputStream(stdErr).use { fileInputStream ->
                            fileInputStream.bufferedReader().use { bufferedReader ->
                                info = normalizeTarStdErr(
                                    context.packageName,
                                    bufferedReader.readText()
                                )
                            }
                        }
                    }.onFailure {
                        val msg = "Failed to get std err."
                        LogHelper.e(TAG, "packageAndCompress#getStdErr", msg, it)
                        status = -1
                        info = msg
                        throw IllegalStateException()
                    }
                }

                val zstdCompression = async(Dispatchers.IO) {
                    while (!fileSystem.exists(stdOut)) {
                        delay(100)
                    }

                    runCatching {
                        compressTarStream(fileSystem, stdOut, targetFile, callback)
                    }.onFailure { exception ->
                        errorMessage = "Zstd compression failed: ${exception.message}"
                        Log.e(TAG, errorMessage, exception)
                        throw exception
                    }
                }

                tarPackaging.await()
                getStdErr.await()
                Log.i(TAG, "stdErr: $info")
                zstdCompression.await()
            }

            if (fileSystem.exists(targetFile)) {
                val compressedSize = fileSystem.length(targetFile)
                val md5 = fileSystem.md5(targetFile)
                callback.onDone(originalSize, compressedSize, md5)
            } else {
                callback.onError(
                    errorMessage.ifEmpty { "Compression completed but target file not found" }
                )
            }
        } catch (e: CancellationException) {
            callback.onError("Compression cancelled")
            runCatching { fileSystem.delete(targetFile) }
        } catch (e: Exception) {
            callback.onError(e.message ?: "Compression failed")
            runCatching { fileSystem.delete(targetFile) }
        } finally {
            runCatching {
                fileSystem.delete(stdOut)
                fileSystem.delete(stdErr)
            }
        }
    }


}
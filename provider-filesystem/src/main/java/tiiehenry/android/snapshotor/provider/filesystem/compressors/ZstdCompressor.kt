package tiiehenry.android.snapshotor.provider.filesystem.compressors

import android.content.Context
import android.system.Os
import android.util.Log
import com.github.luben.zstd.ZstdOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import tiiehenry.android.snapshotor.file.CountingOutputStream
import tiiehenry.android.snapshotor.file.ICompressCallback
import tiiehenry.android.snapshotor.file.IFileSystem
import tiiehenry.android.snapshotor.fs.CompressState
import tiiehenry.android.snapshotor.provider.filesystem.IAlgorithmCompressor
import tiiehenry.android.snapshotor.provider.filesystem.MD5Utils
import tiiehenry.android.snapshotor.task.ITaskHandler
import tiiehenry.android.snapshotor.util.LogHelper
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean

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

    const val TMP_PARCEL_PREFIX = "parcel-"
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

        // 创建临时tar文件用于流式处理
        val tempTarFile = fileSystem.createTempFile("zstd_stream_", ".tar")

        var status = 0
        var info = ""

        val stdOut = File.createTempFile(TMP_FIFO_PREFIX, TMP_SUFFIX)
//        stdOut.delete()
        val stdErr = File.createTempFile(TMP_FIFO_PREFIX, TMP_SUFFIX)
//        stdErr.delete()
        try {
            coroutineScope {
                // 并发执行tar打包和Zstd压缩
                val tarPackaging = async(Dispatchers.IO) {
                    runCatching {
                        // 使用现有的createTarArchive方法进行打包
                        fileSystem.createTarArchive(
                            sourceDir,
                            tempTarFile,
                            excludes,
                            excludeFiles,
                            stdErr.path,
                            stdOut.path
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
                                info =normalizeTarStdErr(context.packageName,bufferedReader.readText())
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
                    // 等待tar文件开始创建后再进行压缩
                    while (!File(tempTarFile).exists()) {
                        delay(100) // 等待100ms
                    }

                    runCatching {
                        compressTarStream(fileSystem, tempTarFile, targetFile, callback)
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
            if (File(targetFile).exists()) {
                val compressedSize = File(targetFile).length()
                val md5 = MD5Utils.getFileMD5(targetFile)
                // 原始大小可以通过tar文件获取
                val originalSize = File(tempTarFile).length()
                callback.onDone(originalSize, compressedSize, md5)
            } else {
                callback.onError(errorMessage.ifEmpty { "Compression completed but target file not found" })
            }

        } catch (e: CancellationException) {
            callback.onError("Compression cancelled")
            // 清理部分完成的文件
            runCatching { File(targetFile).delete() }
        } catch (e: Exception) {
            callback.onError(e.message ?: "Compression failed")
            // 清理失败的文件
            runCatching { File(targetFile).delete() }
        } finally {
            // 清理临时tar文件
            runCatching { File(tempTarFile).delete() }
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
                val fis = android.os.ParcelFileDescriptor.AutoCloseInputStream(parcelInput)
                outputStream.use { parcelOutput ->
                    val fos = android.os.ParcelFileDescriptor.AutoCloseOutputStream(parcelOutput)
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


}
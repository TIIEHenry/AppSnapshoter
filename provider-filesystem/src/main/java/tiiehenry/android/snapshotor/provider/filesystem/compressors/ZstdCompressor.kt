package tiiehenry.android.snapshotor.provider.filesystem.compressors

import com.github.luben.zstd.ZstdOutputStream
import tiiehenry.android.snapshotor.file.ICompressCallback
import tiiehenry.android.snapshotor.file.IFileSystem
import tiiehenry.android.snapshotor.fs.CompressState
import tiiehenry.android.snapshotor.provider.filesystem.IAlgorithmCompressor
import tiiehenry.android.snapshotor.provider.filesystem.MD5Utils
import tiiehenry.android.snapshotor.task.ITaskHandler
import java.util.concurrent.atomic.AtomicBoolean

object ZstdCompressor : IAlgorithmCompressor {
    override fun compress(
        fileSystem: IFileSystem,
        dir: String,
        targetFile: String,
        excludes: List<String>,
        excludeFiles: List<String>,
        callback: ICompressCallback
    ): ITaskHandler {
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
                doCompress(fileSystem, dir, targetFile, excludes, excludeFiles, callback)
            }

            override fun cancel() {
                isCancel.set(true)
            }
        }
    }

    private fun doCompress(
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

        // 创建临时tar文件
        val tempTarFile = fileSystem.createTempFile("zstd_compress_", ".tar")

        try {
            // 第一步：使用tar命令打包目录
            val tarSuccess =
                createTarArchive(fileSystem, dir, tempTarFile, excludes, excludeFiles, callback)
            if (!tarSuccess) {
                callback.onError("tar packaging failed")
                return
            }

            //计算原始大小（tar文件大小）
            val originalSize = fileSystem.length(tempTarFile)

            // 第二步：使用Zstd压缩tar文件
            compressTarWithZstd(fileSystem,tempTarFile, targetFile, originalSize, callback)

            // 计算压缩后的文件MD5
            val md5 = MD5Utils.getFileMD5(targetFile)
            callback.onDone(originalSize, fileSystem.length(targetFile), md5)

        } catch (e: InterruptedException) {
            callback.onError("compression cancelled")
        } catch (e: Exception) {
            e.printStackTrace()
            callback.onError(e.message ?: "compress failed")
        } finally {
            //清理临时tar文件
            if (fileSystem.exists(tempTarFile)) {
                fileSystem.delete(tempTarFile)
            }
        }
    }

    /**
     * 使用tar命令创建归档文件
     */
    private fun createTarArchive(
        fileSystem: IFileSystem,
        sourceDir: String,
        tarFile: String,
        excludes: List<String>,
        excludeFiles: List<String>,
        callback: ICompressCallback
    ): Boolean {
        try {
            // 使用 IFileSystem 的 createTarArchive 方法
            fileSystem.createTarArchive(sourceDir, tarFile, excludes, excludeFiles)
            return true

        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * 使用Zstd压缩tar文件
     */
    private fun compressTarWithZstd(
        fileSystem: IFileSystem,
        tarFile: String,
        targetFile: String,
        originalSize: Long,
        callback: ICompressCallback
    ) {
        var compressedSize = 0L
        var lastProgressTime = System.currentTimeMillis()
        var lastCompressedSize = 0L

        val inputStream = fileSystem.openInputStream(tarFile)
        val outputStream = fileSystem.openOutputStream(targetFile)

        if (inputStream != null && outputStream != null) {
            inputStream.use { parcelInput ->
                val fis = android.os.ParcelFileDescriptor.AutoCloseInputStream(parcelInput)
                outputStream.use { parcelOutput ->
                    val fos = android.os.ParcelFileDescriptor.AutoCloseOutputStream(parcelOutput)
                    ZstdOutputStream(fos).use { zos ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int

                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                            zos.write(buffer, 0, bytesRead)
                            compressedSize += bytesRead

                            //1000ms更新一次进度
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastProgressTime >= 1000) {
                                val progress = (compressedSize * 100 / originalSize).toInt()
                                val kbPerS =
                                    (compressedSize - lastCompressedSize) / (currentTime - lastProgressTime)
                                callback.onProgress(progress, kbPerS)
                                lastProgressTime = currentTime
                                lastCompressedSize = compressedSize
                            }

                            // 检查是否被取消
                            if (Thread.currentThread().isInterrupted) {
                                throw InterruptedException("Compression cancelled")
                            }
                        }
                    }
                }
            }
        } else {
            throw Exception("Failed to open input or output stream")
        }
    }


}
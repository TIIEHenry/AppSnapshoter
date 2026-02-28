package tiiehenry.android.snapshotor.provider.filesystem.compressors

import android.content.Context
import tiiehenry.android.snapshotor.file.ICompressCallback
import tiiehenry.android.snapshotor.file.IFileSystem
import tiiehenry.android.snapshotor.fs.CompressState
import tiiehenry.android.snapshotor.provider.filesystem.IAlgorithmCompressor
import tiiehenry.android.snapshotor.provider.filesystem.MD5Utils
import tiiehenry.android.snapshotor.task.ITaskHandler
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipCompressor : IAlgorithmCompressor {
    override fun compress(
        context: Context,
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
                return "zip:" + dir + ">" + targetFile
            }

            override fun state(): Int {
                return state
            }

            override fun start() {
                state = CompressState.COMPRESS_STATE_RUNNING
                doCompress(dir, targetFile, excludes, callback)
            }

            override fun cancel() {
                isCancel.set(true)
            }


        }
    }

    override fun compressMultiple(
        context: Context,
        fileSystem: IFileSystem,
        files: List<String>,
        targetFile: String,
        callback: ICompressCallback
    ): ITaskHandler {
        return object : ITaskHandler.Stub() {
            var state = CompressState.COMPRESS_STATE_NONE
            var isCancel = AtomicBoolean(false)
            override fun id(): String {
                return "zip:multiple>${targetFile}"
            }

            override fun state(): Int {
                return state
            }

            override fun start() {
                state = CompressState.COMPRESS_STATE_RUNNING
                doCompressMultiple(files, targetFile, callback)
            }

            override fun cancel() {
                isCancel.set(true)
            }
        }
    }

    private fun doCompressMultiple(
        files: List<String>,
        targetFile: String,
        callback: ICompressCallback
    ) {
        if (files.isEmpty()) {
            callback.onError("no files to compress")
            return
        }

        val target = File(targetFile)
        if (target.exists()) {
            callback.onError("target exists")
            return
        }

        callback.onStart()
        target.parentFile?.mkdirs()

        try {
            // 验证所有文件存在且计算总大小
            val filesToCompress = mutableListOf<Pair<File, String>>()
            var totalSize = 0L

            files.forEach { filePath ->
                val file = File(filePath)
                if (!file.exists()) {
                    callback.onError("file not exists: $filePath")
                    return
                }

                if (file.isDirectory) {
                    // 如果是目录，递归收集所有文件
                    collectFiles(file, file.name, emptyList(), filesToCompress)
                } else {
                    filesToCompress.add(file to file.name)
                }
                totalSize += file.length()
            }

            if (filesToCompress.isEmpty()) {
                callback.onError("no files to compress")
                return
            }

            var compressedSize = 0L
            var lastProgressTime = System.currentTimeMillis()
            var lastCompressedSize = 0L

            ZipOutputStream(target.outputStream()).use { zos ->
                filesToCompress.forEach { (file, name) ->
                    val entry = ZipEntry(name)
                    zos.putNextEntry(entry)

                    FileInputStream(file).use { fis ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                            zos.write(buffer, 0, bytesRead)
                            compressedSize += bytesRead

                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastProgressTime >= 1000) {
                                val kbPerS =
                                    (compressedSize - lastCompressedSize) / (currentTime - lastProgressTime)
                                callback.onProgress(compressedSize, kbPerS)
                                lastProgressTime = currentTime
                                lastCompressedSize = compressedSize
                            }
                        }
                    }

                    zos.closeEntry()
                }
            }

            val md5 = MD5Utils.getFileMD5(target)
            callback.onDone(totalSize, target.length(), md5)

        } catch (e: Exception) {
            e.printStackTrace()
            callback.onError(e.message ?: "compress failed")
            if (target.exists()) {
                target.delete()
            }
        }
    }

    private fun doCompress(
        dir: String,
        targetFile: String,
        excludes: List<String>,
        callback: ICompressCallback
    ) {
        val sourceDir = File(dir)
        if (!sourceDir.exists()) {
            callback.onError("source not exists")
            return
        }
        val target = File(targetFile)
        if (target.exists()) {
            callback.onError("target exists")
            return
        }
        callback.onStart()
        target.parentFile?.mkdirs()

        try {
            // 计算总文件大小用于进度报告
            val filesToCompress = mutableListOf<Pair<File, String>>()
            collectFiles(sourceDir, "", excludes, filesToCompress)

            if (filesToCompress.isEmpty()) {
                callback.onError("no files to compress")
                return
            }

            val totalSize = filesToCompress.sumOf { it.first.length() }
            var compressedSize = 0L
            var lastProgressTime = System.currentTimeMillis()
            var lastCompressedSize = 0L

            ZipOutputStream(target.outputStream()).use { zos ->
                filesToCompress.forEach { (file, relativePath) ->
                    val entry = ZipEntry(relativePath)
                    zos.putNextEntry(entry)

                    FileInputStream(file).use { fis ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                            zos.write(buffer, 0, bytesRead)
                            compressedSize += bytesRead

                            // 每隔500ms更新一次进度
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastProgressTime >= 1000) {
                                val kbPerS =
                                    (compressedSize - lastCompressedSize) / (currentTime - lastProgressTime)
                                callback.onProgress(compressedSize, kbPerS)
                                lastProgressTime = currentTime
                                lastCompressedSize = compressedSize
                            }
                        }
                    }

                    zos.closeEntry()
                }
            }

            // 计算压缩后的文件MD5
            val md5 = MD5Utils.getFileMD5(target)
            callback.onDone(totalSize, target.length(), md5)

        } catch (e: Exception) {
            e.printStackTrace()
            callback.onError(e.message ?: "compress failed")
            // 删除失败的文件
            if (target.exists()) {
                target.delete()
            }
        }
    }

    /**
     * 递归收集需要压缩的文件
     */
    private fun collectFiles(
        dir: File,
        parentPath: String,
        excludes: List<String>,
        result: MutableList<Pair<File, String>>
    ) {
        dir.listFiles()?.forEach { file ->
            val relativePath = if (parentPath.isEmpty()) file.name else "$parentPath/${file.name}"

            // 检查是否在排除列表中
            if (excludes.contains(relativePath)) {
                return@forEach
            }

            if (file.isDirectory) {
                collectFiles(file, relativePath, excludes, result)
            } else {
                result.add(file to relativePath)
            }
        }
    }

}

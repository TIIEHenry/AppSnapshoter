package tiiehenry.android.snapshotor.provider.filesystem

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.runBlocking
import tiiehenry.android.snapshotor.file.FileSystemManagerRootService
import tiiehenry.android.snapshotor.file.FileSystemRootServiceClient
import tiiehenry.android.snapshotor.file.IFileCompressor
import tiiehenry.android.snapshotor.file.IFileSystem
import tiiehenry.android.snapshotor.fs.IFileType
import tiiehenry.android.snapshotor.provider.FileSystemProvider
import java.io.File
import java.util.concurrent.CompletableFuture

class FileSystemProviderImpl(
    hostContext: Context,
    pluginContext: Context
) : FileSystemProvider(hostContext, pluginContext) {

    private lateinit var fsmFuture: CompletableFuture<IBinder>
    val serviceClient = FileSystemRootServiceClient.getInstance()

    override fun onInstall() {
        fsmFuture = CompletableFuture<IBinder>()
        RootService.bind(
            Intent(pluginContext, FileSystemManagerRootService::class.java),
            object : ServiceConnection {
                override fun onServiceConnected(
                    name: ComponentName,
                    service: IBinder
                ) {
                    fsmFuture.complete(service)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    fsmFuture.completeExceptionally(Exception("Service disconnected"))
                }
            })
        serviceClient.fetchRemote(pluginContext)
    }

    override fun provide(): IFileSystem {
        if (serviceClient.waitFetch(pluginContext) == null) {
            throw Exception("FileSystemRootService is not available")
        }
        val fileSystemManager = FileSystemManager.getRemote(fsmFuture.get())
        return FileSystemImpl(serviceClient, fileSystemManager, pluginContext)
    }

    private inner class FileSystemImpl(
        val rootServiceClient: FileSystemRootServiceClient,
        val fileSystemManager: FileSystemManager,
        val context: Context
    ) :
        IFileSystem.Stub() {

        val fileCompressor = FileCompressor(this, context)

        private fun getRootService() = rootServiceClient.client!!

        override fun fileType(path: String): Int {
            try {
                val file = fileSystemManager.getFile(path)
                if (!file.exists()) {
                    return IFileType.TYPE_NONE
                }
                if (file.isFile) {
                    return IFileType.TYPE_FILE
                } else if (file.isDirectory) {
                    return IFileType.TYPE_DIR
                }
                return IFileType.TYPE_OTHER
            } catch (e: Exception) {
                e.printStackTrace()
                return IFileType.TYPE_OTHER
            }
        }

        override fun listDir(path: String?): MutableList<String> {
            if (path == null) return mutableListOf()
            val file = fileSystemManager.getFile(path)
            return file.list()?.toMutableList() ?: mutableListOf()
        }

        override fun calculateSize(path: String?): Long {
            if (path == null) return 0L
            val rootService = getRootService()
            return runBlocking { rootService.calculateTreeSize(path) }
        }

        override fun mkdirs(path: String): Boolean {
            return try {
                fileSystemManager.getFile(path).mkdirs()
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        override fun delete(path: String): Boolean {
            return try {
                fileSystemManager.getFile(path).delete()
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        override fun exists(path: String): Boolean {
            return try {
                fileSystemManager.getFile(path).exists()
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        override fun getParent(path: String): String? {
            val file = fileSystemManager.getFile(path)
            return file.parent
        }

        override fun length(path: String): Long {
            val file = fileSystemManager.getFile(path)
            return file.length()
        }

        override fun getLastModifiedTime(path: String?): Long {
            if (path == null) return 0L
            return try {
                fileSystemManager.getFile(path).lastModified()
            } catch (e: Exception) {
                e.printStackTrace()
                0L
            }
        }

        override fun setLastModifiedTime(path: String?, time: Long): Boolean {
            if (path == null) return false
            return try {
                fileSystemManager.getFile(path).setLastModified(time)
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        override fun md5(file: String): String? {
            val rootService = getRootService()
            return runBlocking { rootService.md5(file) }
        }

        override fun getUid(path: String?): Int {
            if (path == null) return -1
            val rootService = getRootService()
            return runBlocking { rootService.getUid(path) }
        }

        override fun setUid(path: String?, uid: Int): Boolean {
            if (path == null) return false
            val rootService = getRootService()
            return runBlocking { rootService.setUid(path, uid) }
        }

        override fun getGid(path: String?): Int {
            if (path == null) return -1
            val rootService = getRootService()
            return runBlocking { rootService.getGid(path) }
        }

        override fun setGid(path: String?, gid: Int): Boolean {
            if (path == null) return false
            val rootService = getRootService()
            return runBlocking { rootService.setGid(path, gid) }
        }

        override fun openFile(path: String, mode: Int): ParcelFileDescriptor? {
            val file = fileSystemManager.getFile(path)
            if (mode and ParcelFileDescriptor.MODE_CREATE != 0 && !file.exists()) {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }
            Log.i("FileSystemProvider", "openFile $path with mode $mode")
            return ParcelFileDescriptor.open(file, mode)
        }

        override fun openInputStream(path: String): ParcelFileDescriptor? {
            return openFile(path, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun openOutputStream(path: String): ParcelFileDescriptor? {
            return openFile(
                path,
                ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE
            )
        }

        override fun createTempFile(prefix: String, suffix: String): String {
            return try {
                val tempFile = File.createTempFile(prefix, suffix)
                tempFile.absolutePath
            } catch (e: Exception) {
                // 如果创建临时文件失败，返回一个基于时间戳的路径
                "/tmp/${prefix}${System.currentTimeMillis()}${suffix}"
            }
        }

        override fun createTarArchive(
            sourceDir: String,
            targetFile: String,
            excludes: List<String>,
            excludeFiles: List<String>,
            stdErr: String,
            stdOut: String
        ) {
            try {
                val rootService = getRootService()
                val sourceFile = File(sourceDir)
                val parentDir = sourceFile.parent ?: "."
                val dirName = sourceFile.name

                // 构建正确的tar命令参数
                val args = mutableListOf("tar", "-cpf", targetFile, "-C", parentDir)

                // 添加排除选项
                excludes.forEach { exclude ->
                    args.add("--exclude=$exclude")
                }

                // 添加排除文件选项
                excludeFiles.forEach { excludeFile ->
                    args.add("-X")
                    args.add(excludeFile)
                }

                // 添加要打包的目录（相对路径）
                args.add(dirName)

                Log.i("FileSystemProvider", "Executing tar command: ${args.joinToString(" ")}")
                // 调用root服务执行tar命令
                val resultCode = rootService.callTarCli(stdOut, stdErr, args.toTypedArray())

                // 检查tar命令执行结果
                if (resultCode != 0) {
                    val errorMsg = "Tar command failed with exit code: $resultCode"
                    Log.e("FileSystemProvider", errorMsg)
                    throw RuntimeException(errorMsg)
                }

                // 验证生成的tar文件是否存在且不为空
                val tarFile = fileSystemManager.getFile(targetFile)
                if (!tarFile.exists() || tarFile.length() == 0L) {
                    val errorMsg = "Tar file was not created or is empty: $targetFile"
                    Log.e("FileSystemProvider", errorMsg)
                    throw RuntimeException(errorMsg)
                }

                Log.i(
                    "FileSystemProvider",
                    "Successfully created tar file: ${tarFile.absolutePath}, size: ${tarFile.length()} bytes"
                )
            } catch (e: Exception) {
                Log.e("FileSystemProvider", "Failed to create tar archive", e)
                throw e
            }
        }

        override fun diff(
            oldDir: String,
            newDir: String,
            addedList: MutableList<String>?,
            removedList: MutableList<String>?,
            changedList: MutableList<String>?,
            keepedList: MutableList<String>?
        ) {
            val rootService = getRootService()
            // Use root service to list directories
            val oldFiles = try {
                runBlocking { rootService.listFilePaths(oldDir, true, false) }
                    .map { it.path.substringAfterLast('/') }
                    .toSet()
            } catch (e: Exception) {
                // Fallback to local access
                File(oldDir).walkTopDown()
                    .filter { it.isFile }
                    .map { it.relativeTo(File(oldDir)).path }
                    .toSet()
            }

            val newFiles = try {
                runBlocking { rootService.listFilePaths(newDir, true, false) }
                    .map { it.path.substringAfterLast('/') }
                    .toSet()
            } catch (e: Exception) {
                // Fallback to local access
                File(newDir).walkTopDown()
                    .filter { it.isFile }
                    .map { it.relativeTo(File(newDir)).path }
                    .toSet()
            }

            // Files in new but not in old
            addedList?.addAll(newFiles - oldFiles)

            // Files in old but not in new
            removedList?.addAll(oldFiles - newFiles)

            // Files in both - check if changed
            val commonFiles = oldFiles.intersect(newFiles)
            commonFiles.forEach { relativePath ->
                val oldFilePath = "$oldDir/$relativePath"
                val newFilePath = "$newDir/$relativePath"

                val sizesMatch = try {
                    runBlocking { rootService.calculateTreeSize(oldFilePath) } ==
                            runBlocking { rootService.calculateTreeSize(newFilePath) }
                } catch (e: Exception) {
                    // Fallback to local comparison
                    File(oldFilePath).length() == File(newFilePath).length()
                }

                val hashesMatch = try {
                    runBlocking { rootService.md5(oldFilePath) } ==
                            runBlocking { rootService.md5(newFilePath) }
                } catch (e: Exception) {
                    // Fallback to local comparison
                    md5(oldFilePath) == md5(newFilePath)
                }

                if (!sizesMatch || !hashesMatch) {
                    changedList?.add(relativePath)
                } else {
                    keepedList?.add(relativePath)
                }
            }
        }

        override fun getCompressor(): IFileCompressor {
            return fileCompressor
        }
    }
}

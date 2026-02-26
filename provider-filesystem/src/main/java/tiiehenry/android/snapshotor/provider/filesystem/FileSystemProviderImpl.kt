package tiiehenry.android.snapshotor.provider.filesystem

import android.content.Context
import android.os.ParcelFileDescriptor
import tiiehenry.android.snapshotor.file.FileSystemRootServiceClient
import tiiehenry.android.snapshotor.file.IBinaryCallback
import tiiehenry.android.snapshotor.file.IFileCompressor
import tiiehenry.android.snapshotor.provider.FileSystemProvider
import tiiehenry.android.snapshotor.file.IFileSystem
import tiiehenry.android.snapshotor.fs.IFileType
import java.io.File
import kotlinx.coroutines.runBlocking

class FileSystemProviderImpl(
    hostContext: Context,
    pluginContext: Context
) : FileSystemProvider(hostContext, pluginContext) {

    override fun provide(): IFileSystem {
        return FileSystemImpl()
    }

    private inner class FileSystemImpl : IFileSystem.Stub() {

        val fileCompressor = FileCompressor(this)
        
        private fun getRootService() = FileSystemRootServiceClient.getInstance().fetchRemote(hostContext)!!

        override fun fileType(path: String): Int {
            return try {
                val rootService = getRootService()
                val exists = runBlocking { rootService.exists(path) }
                if (!exists) {
                    IFileType.TYPE_NONE
                } else {
                    val paths = runBlocking { rootService.listFilePaths(path, true, true) }
                    if (paths.isEmpty()) {
                        // Path exists but is empty, check if it's a file or directory
                        // For now, assume it's a directory if we can list it, otherwise file
                        // More accurate approach would require more detailed filesystem info
                        if (path.endsWith("/")) IFileType.TYPE_DIR else IFileType.TYPE_FILE
                    } else {
                        val pathInfo = paths.firstOrNull { it.path == path }
                        when (pathInfo?.type) {
                            0 -> IFileType.TYPE_FILE
                            1 -> IFileType.TYPE_DIR
                            else -> IFileType.TYPE_OTHER
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback to local file access if root service fails
                val file = File(path)
                when {
                    !file.exists() -> IFileType.TYPE_NONE
                    file.isDirectory -> IFileType.TYPE_DIR
                    file.isFile -> IFileType.TYPE_FILE
                    else -> IFileType.TYPE_OTHER
                }
            }
        }

        override fun listDir(path: String?): MutableList<String> {
            if (path == null) return mutableListOf()
            return try {
                val rootService = getRootService()
                val paths = runBlocking { rootService.listFilePaths(path, true, true) }
                val result = mutableListOf<String>()
                paths.forEach { pathInfo ->
                    val fileName = File(pathInfo.path).name
                    result.add(fileName)
                }
                result
            } catch (e: Exception) {
                // Fallback to local file access if root service fails
                val file = File(path)
                if (!file.exists() || !file.isDirectory) return mutableListOf()
                file.list()?.toMutableList() ?: mutableListOf()
            }
        }

        override fun calculateSize(path: String?): Long {
            if (path == null) return 0L
            return try {
                val rootService = getRootService()
                runBlocking { rootService.calculateTreeSize(path) }
            } catch (e: Exception) {
                // Fallback to local file access if root service fails
                File(path).walkTopDown().filter { it.isFile }.map { it.length() }.sum()
            }
        }

        override fun mkdirs(path: String): Boolean {
            return try {
                val rootService = getRootService()
                runBlocking { rootService.mkdirs(path) }
            } catch (e: Exception) {
                // Fallback to local file access if root service fails
                File(path).mkdirs()
            }
        }

        override fun delete(path: String): Boolean {
            return try {
                val rootService = getRootService()
                runBlocking { rootService.deleteRecursively(path) }
            } catch (e: Exception) {
                // Fallback to local file access if root service fails
                File(path).deleteRecursively()
            }
        }

        override fun exists(path: String): Boolean {
            return try {
                val rootService = getRootService()
                runBlocking { rootService.exists(path) }
            } catch (e: Exception) {
                // Fallback to local file access if root service fails
                File(path).exists()
            }
        }

        override fun getParent(path: String): String? {
            return try {
                File(path).parent
            } catch (e: Exception) {
                null
            }
        }

        override fun length(path: String): Long {
            return try {
                val rootService = getRootService()
                runBlocking { rootService.calculateTreeSize(path) }
            } catch (e: Exception) {
                // Fallback to local file access if root service fails
                File(path).length()
            }
        }

        override fun getLastModifiedTime(path: String?): Long {
            if (path == null) return 0L
            return try {
                val rootService = getRootService()
                runBlocking { rootService.getLastModifiedTime(path) }
            } catch (e: Exception) {
                // Fallback to local file access if root service fails
                File(path).lastModified()
            }
        }

        override fun setLastModifiedTime(path: String?, time: Long): Boolean {
            if (path == null) return false
            return try {
                val rootService = getRootService()
                runBlocking { rootService.setLastModifiedTime(path, time) }
            } catch (e: Exception) {
                // Fallback to local file access if root service fails
                File(path).setLastModified(time)
            }
        }

        override fun md5(file: String): String? {
            return try {
                val rootService = getRootService()
                runBlocking { rootService.md5(file) }
            } catch (e: Exception) {
                // Fallback to local MD5 calculation if root service fails
                MD5Utils.getFileMD5(file)
            }
        }

        override fun getUid(path: String?): Int {
            if (path == null) return -1
            return try {
                val rootService = getRootService()
                runBlocking { rootService.getUid(path) }
            } catch (e: Exception) {
                // Fallback to local file access if root service fails
                -1
            }
        }

        override fun setUid(path: String?, uid: Int): Boolean {
            if (path == null) return false
            return try {
                val rootService = getRootService()
                runBlocking { rootService.setUid(path, uid) }
            } catch (e: Exception) {
                // Fallback to local file access if root service fails
                false
            }
        }

        override fun getGid(path: String?): Int {
            if (path == null) return -1
            return try {
                val rootService = getRootService()
                runBlocking { rootService.getGid(path) }
            } catch (e: Exception) {
                // Fallback to local file access if root service fails
                -1
            }
        }

        override fun setGid(path: String?, gid: Int): Boolean {
            if (path == null) return false
            return try {
                val rootService = getRootService()
                runBlocking { rootService.setGid(path, gid) }
            } catch (e: Exception) {
                // Fallback to local file access if root service fails
                false
            }
        }

        override fun openFile(path: String, mode: Int): ParcelFileDescriptor? {
            return try {
                val rootService = getRootService()
                runBlocking { rootService.openFile(path, mode) }
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to local file access if root service fails
                val file = File(path)
                if (mode and ParcelFileDescriptor.MODE_CREATE != 0 && !file.exists()) {
                    file.parentFile?.mkdirs()
                    file.createNewFile()
                }
                ParcelFileDescriptor.open(file, mode)
            }
        }

        override fun openInputStream(path: String): ParcelFileDescriptor? {
            return openFile(path, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun openOutputStream(path: String): ParcelFileDescriptor? {
            return openFile(path, ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE)
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

        override fun createTarArchive(sourceDir: String, targetFile: String, excludes: List<String>, excludeFiles: List<String>) {
            try {
                val rootService = getRootService()
                // 构建tar命令参数
                val args = mutableListOf("tar", "-cpf", targetFile, "-C", File(sourceDir).parent ?: ".")
                
                // 添加排除选项
                excludes.forEach { exclude ->
                    args.add("--exclude=$exclude")
                }

                // 添加排除选项
                excludeFiles.forEach { exclude ->
                    args.add("-X=$exclude")
                }

                // 添加要打包的目录
                args.add(File(sourceDir).name)
                
                // 调用root服务执行tar命令
                runBlocking { rootService.callTarCli("", "", args.toTypedArray()) }
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback到本地tar命令执行
                val process = ProcessBuilder("tar", "-cpf", targetFile, "-C", File(sourceDir).parent ?: ".", File(sourceDir).name)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start()
                process.waitFor()
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

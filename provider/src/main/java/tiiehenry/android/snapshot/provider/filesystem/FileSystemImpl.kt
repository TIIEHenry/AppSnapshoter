package tiiehenry.android.snapshot.provider.filesystem

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.topjohnwu.superuser.nio.FileSystemManager
import com.topjohnwu.superuser.nio.ExtendedFile
import kotlinx.coroutines.runBlocking
import tiiehenry.android.snapshot.file.IFileCompressor
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.fs.IFileType
import tiiehenry.android.snapshot.provider.service.ISnapShotRootService
import java.io.File

class FileSystemImpl(
    private val rootService: () -> ISnapShotRootService,
    private val manager: () -> FileSystemManager,
    val context: Context
) :
    IFileSystem {

    val fileCompressor = FileCompressor(this, context)

    private val fileSystemManager: FileSystemManager get() = manager()

    private fun getRootService() = rootService()

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
            deleteEntry(fileSystemManager.getFile(path))
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun deleteEntry(file: ExtendedFile): Boolean {
        // Kotlin File.deleteRecursively follows directory symlinks. Preserve
        // remote child objects and remove links without traversing their target,
        // including links nested below the first level.
        if (file.isSymlink || !file.isDirectory) return file.delete()
        val children = file.list() ?: return false
        return children.all { deleteEntry(file.getChildFile(it)) } && file.delete()
    }

    override fun cleanDir(path: String): Boolean {
        return try {
            val file = fileSystemManager.getFile(path)
            if (!file.exists() || !file.isDirectory) {
                return false
            }
            val children = file.list() ?: return false
            children.all { childName ->
                val child = file.getChildFile(childName)
                deleteEntry(child)
            }
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
        return getRootService().openFile(path, mode)
    }

    override fun openInputStream(path: String): ParcelFileDescriptor? {
        return openFile(path, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun openOutputStream(path: String): ParcelFileDescriptor? {
        return openFile(
            path,
            ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE
        )
    }

    override fun createTempFile(prefix: String, suffix: String): String =
        File.createTempFile(prefix, suffix, context.cacheDir).absolutePath

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
            val sourceType = fileType(sourceDir)
            require(sourceType == IFileType.TYPE_FILE || sourceType == IFileType.TYPE_DIR) {
                "Unsupported archive source: $sourceDir"
            }
            val directory = if (sourceType == IFileType.TYPE_FILE) {
                sourceFile.parent ?: "."
            } else sourceFile.absolutePath
            val member = if (sourceType == IFileType.TYPE_FILE) sourceFile.name else "."
            val args = mutableListOf("tar", "-cpf", targetFile, "-C", directory)

            // 添加排除选项
            excludes.forEach { exclude ->
                args.add("--exclude=$exclude")
            }

            // 添加排除文件选项
            excludeFiles.forEach { excludeFile ->
                args.add("-X")
                args.add(excludeFile)
            }

            // 添加要打包的文件（相对路径）
            args.add("--")
            args.add(member)

            Log.i("FileSystemProvider", "Executing tar command: ${args.joinToString(" ")}")
            // 调用root服务执行tar命令
            val resultCode = rootService.callTarCli(null,stdOut, stdErr, args.toTypedArray())

            // 检查tar命令执行结果
            if (resultCode != 0) {
                val errorMsg = "Tar command failed with exit code: $resultCode"
                Log.e("FileSystemProvider", errorMsg)
                throw RuntimeException(errorMsg)
            }

//                // 验证生成的tar文件是否存在且不为空
//                val tarFile = fileSystemManager.getFile(targetFile)
//                if (!tarFile.exists() || tarFile.length() == 0L) {
//                    val errorMsg = "Tar file was not created or is empty: $targetFile"
//                    Log.e("FileSystemProvider", errorMsg)
//                    throw RuntimeException(errorMsg)
//                }
//
//                Log.i(
//                    "FileSystemProvider",
//                    "Successfully created tar file: ${tarFile.absolutePath}, size: ${tarFile.length()} bytes"
//                )
        } catch (e: Exception) {
            Log.e("FileSystemProvider", "Failed to create tar archive", e)
            throw e
        }
    }

    override fun createTarArchiveForMultiple(
        files: List<String>,
        targetFile: String,
        stdErr: String,
        stdOut: String
    ) {
        try {
            val rootService = getRootService()

            // 同名最后一段用公共父目录相对路径，名字经 --add-file= 传入，避免被当成选项。
            val args = buildMultiTarCommand(targetFile, files)

            Log.i(
                "FileSystemProvider",
                "Executing tar command for multiple files: ${args.joinToString(" ")}"
            )
            // 调用root服务执行tar命令
            val resultCode = rootService.callTarCli(null, stdOut, stdErr, args.toTypedArray())

            // 检查tar命令执行结果
            if (resultCode != 0) {
                val errorMsg = "Tar command failed with exit code: $resultCode"
                Log.e("FileSystemProvider", errorMsg)
                throw RuntimeException(errorMsg)
            }

            Log.i(
                "FileSystemProvider",
                "Successfully created tar file for multiple files: $targetFile"
            )
        } catch (e: Exception) {
            Log.e("FileSystemProvider", "Failed to create tar archive for multiple files", e)
            throw e
        }
    }

    override fun getCompressor(): IFileCompressor {
        return fileCompressor
    }

    override fun mkfifo(path: String, mode: Int): Boolean {
        return try {
            Os.mkfifo(path, mode)
            true
        } catch (e: Exception) {
            Log.e("FileSystemProvider", "Failed to create FIFO: $path", e)
            false
        }
    }

    override fun isFifo(path: String): Boolean {
        return try {
            val stat = Os.stat(path)
            (stat.st_mode and OsConstants.S_IFMT) == OsConstants.S_IFIFO
        } catch (e: Exception) {
            Log.e("FileSystemProvider", "Failed to check if $path is FIFO", e)
            false
        }
    }

    override fun extractTar(tarFifo: String, targetDir: String): Boolean {
        return try {
            val rootService = getRootService()
            runBlocking { rootService.extractTar(tarFifo, targetDir) }
        } catch (e: Exception) {
            Log.e("FileSystemProvider", "Failed to extract tar from FIFO", e)
            false
        }
    }

    override fun extractTarFromFd(input: ParcelFileDescriptor, targetDir: String): Boolean =
        getRootService().extractTarFromFd(input, targetDir)

    override fun move(sourcePath: String, targetPath: String): Boolean {
        return try {
            val sourceFile = fileSystemManager.getFile(sourcePath)
            val targetFile = fileSystemManager.getFile(targetPath)

            // 检查源文件是否存在
            if (!sourceFile.exists()) {
                Log.e("FileSystemProvider", "Source path does not exist: $sourcePath")
                return false
            }

            // 检查目标是否已存在
            if (targetFile.exists()) {
                Log.e("FileSystemProvider", "Target path already exists: $targetPath")
                return false
            }

            // 确保目标父目录存在
            targetFile.parentFile?.mkdirs()

            // 执行重命名/移动操作
            sourceFile.renameTo(targetFile)
        } catch (e: Exception) {
            Log.e("FileSystemProvider", "Failed to move from '$sourcePath' to '$targetPath'", e)
            false
        }
    }

    override fun copyRecursively(source: String, target: String, overwrite: Boolean): Boolean {
        return try {
            val rootService = getRootService()
            rootService.copyRecursively(source, target, overwrite)
        } catch (e: Exception) {
            Log.e("FileSystemProvider", "Failed to copy from '$source' to '$target'", e)
            false
        }
    }
}
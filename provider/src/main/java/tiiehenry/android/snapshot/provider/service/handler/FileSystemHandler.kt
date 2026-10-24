package tiiehenry.android.snapshot.provider.service.handler

import android.content.Context
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import nota.android.io.NativeFileSystem
import tiiehenry.android.compress.zstd.TarJNI
import tiiehenry.android.snapshot.fs.IFileType
import tiiehenry.android.snapshot.provider.appmanager.util.LogHelper
import tiiehenry.android.snapshot.provider.appmanager.util.PathHelper
import tiiehenry.android.snapshot.provider.filesystem.FsKind
import tiiehenry.android.snapshot.provider.filesystem.FsStatus
import tiiehenry.android.snapshot.provider.filesystem.LinkOps
import tiiehenry.android.snapshot.provider.filesystem.LinkSafeFiles
import tiiehenry.android.snapshot.provider.service.bean.StatFsResult
import tiiehenry.android.snapshot.provider.filesystem.tarCliCommand
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest

class FileSystemHandler(private val context: Context) {
    private val linkSafe = LinkSafeFiles(AndroidLinkOps())

    // ==================== 通用工具方法 ====================

    private fun writeToParcel(context: Context, block: (Parcel) -> Unit): ParcelFileDescriptor {
        val parcel = Parcel.obtain()
        parcel.setDataPosition(0)
        block(parcel)
        val tmpFile = File.createTempFile(
            PathHelper.TMP_PARCEL_PREFIX,
            PathHelper.TMP_SUFFIX,
            context.cacheDir
        )
        tmpFile.delete()
        tmpFile.createNewFile()
        tmpFile.writeBytes(parcel.marshall())
        val pfd = ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_WRITE)
        tmpFile.delete()
        parcel.recycle()
        return pfd
    }

    private fun readFromParcel(pfd: ParcelFileDescriptor, block: (Parcel) -> Unit) {
        val stream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
        val bytes = stream.readBytes()
        val parcel = Parcel.obtain()
        parcel.unmarshall(bytes, 0, bytes.size)
        parcel.setDataPosition(0)
        block(parcel)
        parcel.recycle()
    }

    // ==================== 文件系统方法 ====================

    fun readStatFs(path: String): StatFsResult {
        val statFs = StatFs(path)
        return StatFsResult(statFs.availableBytes, statFs.totalBytes)
    }

    fun readText(path: String): ParcelFileDescriptor {
        return writeToParcel(context) { parcel ->
            parcel.writeString(runCatching { File(path).readText() }.getOrNull() ?: "")
        }
    }

    fun writeText(path: String, pfd: ParcelFileDescriptor): Boolean {
        return try {
            var text = ""
            readFromParcel(pfd) { parcel -> parcel.readString()?.also { text = it } }
            val wrote = linkSafe.writeText(path, text)
            if (wrote) {
                LogHelper.i("FileSystemHandler", "writeText", "Successfully wrote text to $path")
            } else {
                LogHelper.e("FileSystemHandler", "writeText", "Failed to write text to $path")
            }
            wrote
        } catch (e: Exception) {
            LogHelper.e(
                "FileSystemHandler",
                "writeText",
                "Failed to write text to $path: ${e.message}"
            )
            false
        }
    }

    fun calculateTreeSize(path: String): Long {
        return NativeFileSystem.calculateTreeSize(path)
    }

    fun callTarCli(
        pipeFile: String?,
        stdOut: String,
        stdErr: String,
        argv: Array<String>
    ): Int {
        val executable = resolveTarCli() ?: run {
            LogHelper.e("FileSystemHandler", "callTarCli", "libtar-cli.so was not found")
            return -1
        }
        // Open the caller's paths directly. A FIFO read end closed by cancel
        // then fails tar's write, so this call returns while tar is still running.
        val outAnchor = openPipeAnchor(stdOut)
        val errAnchor = openPipeAnchor(stdErr)
        var process: Process? = null
        var finished = false
        return try {
            process = ProcessBuilder(tarCliCommand(executable, argv))
                .redirectInput(tarInputRedirect(pipeFile))
                .redirectOutput(ProcessBuilder.Redirect.to(File(stdOut)))
                .redirectError(ProcessBuilder.Redirect.to(File(stdErr)))
                .start()
            closeAnchor(outAnchor)
            closeAnchor(errAnchor)
            val exitCode = process.waitFor()
            finished = true
            exitCode
        } catch (error: Exception) {
            LogHelper.e("FileSystemHandler", "callTarCli", "Failed to run tar: ${error.message}")
            -1
        } finally {
            closeAnchor(outAnchor)
            closeAnchor(errAnchor)
            if (!finished) process?.destroy()
        }
    }

    private fun resolveTarCli(): String? {
        val cli = File(context.applicationInfo.nativeLibraryDir, "libtar-cli.so")
        return if (cli.isFile) cli.absolutePath else null
    }

    private fun isFifo(path: String): Boolean {
        return try {
            val stat = Os.lstat(path)
            stat.st_mode and OsConstants.S_IFMT == OsConstants.S_IFIFO
        } catch (_: ErrnoException) {
            false
        }
    }

    /** Holds a FIFO open so the following O_WRONLY open cannot block. */
    private fun openPipeAnchor(path: String): FileDescriptor? {
        if (!isFifo(path)) return null
        return Os.open(
            path,
            OsConstants.O_RDWR or OsConstants.O_NONBLOCK or OsConstants.O_CLOEXEC,
            0
        )
    }

    private fun closeAnchor(anchor: FileDescriptor?) {
        if (anchor == null || !anchor.valid()) return
        runCatching { Os.close(anchor) }
    }

    private fun tarInputRedirect(pipeFile: String?): ProcessBuilder.Redirect {
        val path = if (pipeFile.isNullOrEmpty()) "/dev/null" else pipeFile
        return ProcessBuilder.Redirect.from(File(path))
    }

    fun mkdirs(path: String): Boolean {
        return runCatching {
            val file = File(path)
            if (file.exists().not()) file.mkdirs() else true
        }.getOrNull() ?: false
    }

    fun exists(path: String): Boolean {
        return runCatching { File(path).exists() }.getOrNull() ?: false
    }

    fun fileType(path: String): Int {
        val file = File(path)
        return when {
            !file.exists() -> IFileType.TYPE_NONE
            file.isDirectory -> IFileType.TYPE_DIR
            file.isFile -> IFileType.TYPE_FILE
            else -> IFileType.TYPE_OTHER
        }
    }

    fun deleteRecursively(path: String): Boolean = linkSafe.deleteRecursively(path)

    fun copyRecursively(source: String, target: String, overwrite: Boolean): Boolean =
        linkSafe.copyRecursively(source, target, overwrite)

    fun getLastModifiedTime(path: String): Long {
        return runCatching { File(path).lastModified() }.getOrNull() ?: 0L
    }

    fun setLastModifiedTime(path: String, time: Long): Boolean {
        return runCatching { File(path).setLastModified(time) }.getOrNull() ?: false
    }

    fun getUid(path: String): Int {
        return runCatching {
            val stat = Os.stat(path)
            stat.st_uid
        }.getOrNull() ?: -1
    }

    fun setUid(path: String, uid: Int): Boolean = linkSafe.setUid(path, uid)

    fun getGid(path: String): Int {
        return runCatching {
            val stat = Os.stat(path)
            stat.st_gid
        }.getOrNull() ?: -1
    }

    fun setGid(path: String, gid: Int): Boolean = linkSafe.setGid(path, gid)

    fun openFile(path: String, mode: Int): ParcelFileDescriptor? {
        return runCatching {
            val file = File(path)
            if (mode and ParcelFileDescriptor.MODE_CREATE != 0 && !file.exists()) {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }
            ParcelFileDescriptor.open(file, mode)
        }.getOrNull()
    }

    fun md5(file: String): String? {
        return runCatching {
            val digest = MessageDigest.getInstance("MD5")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    fun extractTar(tarFile: String, targetDir: String): Boolean {
        if (!File(tarFile).exists()) return false
        return extractTarCommand(targetDir) { stdout, stderr, arguments ->
            TarJNI.callCli(tarFile, stdout, stderr, arguments)
        }
    }

    fun extractTarFromFd(input: ParcelFileDescriptor, targetDir: String): Boolean = runCatching {
        input.use { descriptor ->
            val success = extractTarCommand(targetDir) { stdout, stderr, arguments ->
                TarJNI.callCliWithInputFd(descriptor.fd, stdout, stderr, arguments)
            }
            if (success) {
                // tar stops at the end marker, which may precede the last tar
                // padding bytes. Drain to EOF so the zstd producer can finish
                // and validate its frame checksum without racing a closed reader.
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { stream ->
                    val buffer = ByteArray(32 * 1024)
                    while (stream.read(buffer) != -1) { /* discard tar padding */ }
                    descriptor.checkError()
                }
            }
            success
        }
    }.onFailure {
        LogHelper.e("FileSystemHandler", "extractTarFromFd", "Failed tar stream: ${it.message}")
    }.getOrDefault(false)

    private fun extractTarCommand(
        targetDir: String,
        execute: (String, String, Array<String>) -> Int
    ): Boolean = runCatching {
        val target = File(targetDir)
        check(target.isDirectory || target.mkdirs()) { "Cannot create extraction directory: $targetDir" }
        val stdout = File.createTempFile("tar-stdout-", ".log", context.cacheDir)
        val stderr = File.createTempFile("tar-stderr-", ".log", context.cacheDir)
        try {
            val arguments = arrayOf("tar", "-xpf", "-", "-C", targetDir)
            val exitCode = execute(stdout.absolutePath, stderr.absolutePath, arguments)
            if (exitCode != 0) {
                LogHelper.e("FileSystemHandler", "extractTar", "tar exit $exitCode: ${stderr.readText()}")
            }
            exitCode == 0
        } finally {
            stdout.delete()
            stderr.delete()
        }
    }.onFailure {
        LogHelper.e("FileSystemHandler", "extractTar", "Failed to extract tar: ${it.message}")
    }.getOrDefault(false)
}

/** lstat / lchown / unlink. Does not follow a symlink at the named path. */
private class AndroidLinkOps : LinkOps {
    override fun status(path: String): FsStatus {
        return try {
            val stat = Os.lstat(path)
            val kind = when (stat.st_mode and OsConstants.S_IFMT) {
                OsConstants.S_IFLNK -> FsKind.SYMLINK
                OsConstants.S_IFDIR -> FsKind.DIRECTORY
                OsConstants.S_IFREG -> FsKind.FILE
                else -> FsKind.OTHER
            }
            FsStatus(kind, stat.st_uid, stat.st_gid)
        } catch (error: ErrnoException) {
            if (error.errno == OsConstants.ENOENT) FsStatus(FsKind.MISSING) else throw error
        }
    }

    override fun unlink(path: String) {
        // Public android.system.Os exposes unlink(2) as remove, not unlink.
        Os.remove(path)
    }

    override fun readLink(path: String): String = Os.readlink(path)

    override fun symlink(target: String, path: String) {
        Os.symlink(target, path)
    }

    override fun list(path: String): List<String>? = File(path).list()?.toList()

    override fun removeEmptyDirectory(path: String) {
        // Public android.system.Os has no rmdir. delete() removes an empty directory.
        if (!File(path).delete()) throw IOException(path)
    }

    override fun mkdirs(path: String) {
        val created = File(path).mkdirs()
        if (!created && status(path).kind != FsKind.DIRECTORY) throw IOException(path)
    }

    override fun copyFile(source: String, target: String) {
        File(source).inputStream().use { input ->
            File(target).outputStream().use { output -> input.copyTo(output) }
        }
    }

    override fun lchown(path: String, uid: Int, gid: Int) {
        Os.lchown(path, uid, gid)
    }

    override fun writeFile(path: String, text: String) {
        val file = File(path)
        if (!file.exists() && !file.createNewFile()) throw IOException(path)
        file.writeText(text)
    }
}

package tiiehenry.android.snapshot.provider.service.handler

import android.content.Context
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.system.Os
import nota.android.io.NativeFileSystem
import tiiehenry.android.compress.zstd.TarJNI
import tiiehenry.android.snapshot.fs.IFileType
import tiiehenry.android.snapshot.provider.appmanager.util.LogHelper
import tiiehenry.android.snapshot.provider.appmanager.util.PathHelper
import tiiehenry.android.snapshot.provider.service.bean.StatFsResult
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class FileSystemHandler(private val context: Context) {

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
            val textFile = File(path)
            if (textFile.isDirectory || textFile.exists()) {
                textFile.deleteRecursively()
            }
            textFile.createNewFile()
            textFile.writeText(text)
            LogHelper.i("FileSystemHandler", "writeText", "Successfully wrote text to $path")
            true
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
        return TarJNI.callCli(pipeFile, stdOut, stdErr, argv)
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

    fun deleteRecursively(path: String): Boolean {
        return runCatching { File(path).deleteRecursively() }.getOrNull() ?: false
    }

    fun copyRecursively(source: String, target: String, overwrite: Boolean): Boolean {
        return runCatching { File(source).copyRecursively(File(target), overwrite) }.getOrNull()
            ?: false
    }

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

    fun setUid(path: String, uid: Int): Boolean {
        return runCatching {
            Os.chown(path, uid, -1)
            true
        }.getOrNull() ?: false
    }

    fun getGid(path: String): Int {
        return runCatching {
            val stat = Os.stat(path)
            stat.st_gid
        }.getOrNull() ?: -1
    }

    fun setGid(path: String, gid: Int): Boolean {
        return runCatching {
            Os.chown(path, -1, gid)
            true
        }.getOrNull() ?: false
    }

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

package tiiehenry.android.shapshotor.provider.filesystem

import android.content.Context
import android.os.ParcelFileDescriptor
import tiiehenry.android.shapshotor.file.IFileCompressor
import tiiehenry.android.shapshotor.provider.FileSystemProvider
import tiiehenry.android.shapshotor.file.IFileSystem
import tiiehenry.android.shapshotor.fs.IFileType
import java.io.File

class FileSystemProviderImpl(
    hostContext: Context,
    pluginContext: Context
) : FileSystemProvider(hostContext, pluginContext) {

    override fun provide(): IFileSystem {
        return FileSystemImpl()
    }

    private class FileSystemImpl : IFileSystem.Stub() {

        val fileCompressor = FileCompressor(this)

        override fun fileType(path: String): Int {
            val file = File(path)
            return when {
                !file.exists() -> IFileType.TYPE_NONE
                file.isDirectory -> IFileType.TYPE_DIR
                file.isFile -> IFileType.TYPE_FILE
                else -> IFileType.TYPE_OTHER
            }
        }

        override fun listDir(path: String?): MutableList<String> {
            if (path == null) return mutableListOf()
            val file = File(path)
            if (!file.exists() || !file.isDirectory) return mutableListOf()
            return file.list()?.toMutableList() ?: mutableListOf()
        }

        override fun calculateSize(path: String?): Long {
            if (path == null) return 0L
            return File(path).walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        }

        override fun mkdirs(path: String): Boolean {
            return File(path).mkdirs()
        }

        override fun delete(path: String): Boolean {
            return File(path).deleteRecursively()
        }

        override fun getLastModifiedTime(path: String?): Long {
            if (path == null) return 0L
            return File(path).lastModified()
        }

        override fun setLastModifiedTime(path: String?, time: Long): Boolean {
            if (path == null) return false
            return File(path).setLastModified(time)
        }

        override fun getAccessTime(path: String?): Long {
            // Android doesn't provide direct access time API
            return 0L
        }

        override fun setAccessTime(path: String?, time: Long): Boolean {
            // Android doesn't provide direct access time API
            return false
        }

        override fun md5(file: String): String? {
            return MD5Utils.getFileMD5(file)
        }

        override fun getUid(path: String?): Int {
            // Requires root access or system permissions
            return -1
        }

        override fun setUid(path: String?, uid: Int): Boolean {
            // Requires root access or system permissions
            return false
        }

        override fun getGid(path: String?): Int {
            // Requires root access or system permissions
            return -1
        }

        override fun setGid(path: String?, gid: Int): Boolean {
            // Requires root access or system permissions
            return false
        }

        override fun openFile(path: String, mode: Int): ParcelFileDescriptor? {
            return try {
                // 检查是否需要创建文件
                val file = File(path)
                if (mode and ParcelFileDescriptor.MODE_CREATE != 0 && !file.exists()) {
                    file.parentFile?.mkdirs()
                    file.createNewFile()
                }
                ParcelFileDescriptor.open(file, mode)
            } catch (e: Exception) {
                e.printStackTrace()
                null
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
            val oldFiles = File(oldDir).walkTopDown()
                .filter { it.isFile }
                .map { it.relativeTo(File(oldDir)).path }
                .toSet()

            val newFiles = File(newDir).walkTopDown()
                .filter { it.isFile }
                .map { it.relativeTo(File(newDir)).path }
                .toSet()

            // Files in new but not in old
            addedList?.addAll(newFiles - oldFiles)

            // Files in old but not in new
            removedList?.addAll(oldFiles - newFiles)

            // Files in both - check if changed
            val commonFiles = oldFiles.intersect(newFiles)
            commonFiles.forEach { relativePath ->
                val oldFile = File(oldDir, relativePath)
                val newFile = File(newDir, relativePath)
                if (oldFile.length() != newFile.length() ||
                    md5(oldFile.absolutePath) != md5(newFile.absolutePath)
                ) {
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

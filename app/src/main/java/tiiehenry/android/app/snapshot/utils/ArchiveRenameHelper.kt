package tiiehenry.android.app.snapshot.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.fs.IFileType
import java.io.File

object ArchiveRenameHelper {

    private const val TAG = "ArchiveRenameHelper"

    /**
     * 重命名存档
     * @param fs 文件系统接口
     * @param archivePath 存档的完整路径
     * @param oldName 旧存档名称
     * @param newName 新存档名称
     * @return 是否重命名成功
     */
    suspend fun renameArchive(
        fs: IFileSystem,
        archivePath: String,
        oldName: String,
        newName: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 验证新名称的有效性
            if (!isValidArchiveName(newName)) {
                Log.e(TAG, "Invalid archive name: $newName")
                return@withContext false
            }

            // 检查目标名称是否已存在
            val parentDir = File(archivePath).parent
            val newArchivePath = File(parentDir, newName).absolutePath

            if (fs.fileType(newArchivePath) != IFileType.TYPE_NONE) {
                Log.e(TAG, "Archive with name '$newName' already exists at path: $newArchivePath")
                return@withContext false
            }
            // 使用 IFileSystem.move 方法重命名目录
            if (fs.move(archivePath, newArchivePath)) {
                Log.d(
                    TAG,
                    "Successfully renamed archive from '$oldName' to '$newName'"
                )
                return@withContext true
            } else {
                Log.e(
                    TAG,
                    "Failed to rename archive from '$oldName' to '$newName'"
                )
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error renaming archive from '$oldName' to '$newName'", e)
            return@withContext false
        }
    }

    /**
     * 复制目录内容
     * @param fs 文件系统接口
     * @param sourcePath 源目录路径
     * @param targetPath 目标目录路径
     * @return 是否复制成功
     */
    private suspend fun copyDirectory(
        fs: IFileSystem,
        sourcePath: String,
        targetPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 确保目标目录存在
            if (!fs.mkdirs(targetPath)) {
                Log.e(TAG, "Failed to create target directory: $targetPath")
                return@withContext false
            }

            // 列出源目录中的所有文件和子目录
            val items = fs.listDir(sourcePath)
            for (item in items) {
                val sourceItemPath = "$sourcePath/$item"
                val targetItemPath = "$targetPath/$item"

                when (fs.fileType(sourceItemPath)) {
                    IFileType.TYPE_FILE -> {
                        // 复制文件
                        if (!copyFile(fs, sourceItemPath, targetItemPath)) {
                            Log.e(
                                TAG,
                                "Failed to copy file from '$sourceItemPath' to '$targetItemPath'"
                            )
                            return@withContext false
                        }
                    }

                    IFileType.TYPE_DIR -> {
                        // 递归复制目录
                        if (!copyDirectory(fs, sourceItemPath, targetItemPath)) {
                            Log.e(
                                TAG,
                                "Failed to copy directory from '$sourceItemPath' to '$targetItemPath'"
                            )
                            return@withContext false
                        }
                    }

                    else -> {
                        Log.w(TAG, "Unknown file type for: $sourceItemPath")
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error copying directory from '$sourcePath' to '$targetPath'", e)
            false
        }
    }

    /**
     * 复制单个文件
     * @param fs 文件系统接口
     * @param sourcePath 源文件路径
     * @param targetPath 目标文件路径
     * @return 是否复制成功
     */
    private suspend fun copyFile(fs: IFileSystem, sourcePath: String, targetPath: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // 打开源文件进行读取
                val sourceFd =
                    fs.openFile(sourcePath, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                if (sourceFd == null) {
                    Log.e(TAG, "Failed to open source file: $sourcePath")
                    return@withContext false
                }

                // 确保目标文件的目录存在
                val targetFile = File(targetPath)
                targetFile.parentFile?.mkdirs()

                // 打开目标文件进行写入
                val targetFd = fs.openFile(
                    targetPath,
                    android.os.ParcelFileDescriptor.MODE_CREATE or
                            android.os.ParcelFileDescriptor.MODE_WRITE_ONLY or
                            android.os.ParcelFileDescriptor.MODE_TRUNCATE
                )
                if (targetFd == null) {
                    Log.e(TAG, "Failed to open target file: $targetPath")
                    sourceFd.close()
                    return@withContext false
                }

                // 使用文件描述符进行复制
                val sourceStream = sourceFd.fileDescriptor
                val targetStream = targetFd.fileDescriptor

                // 复制文件内容
                val inputStream = java.io.FileInputStream(sourceStream)
                val outputStream = java.io.FileOutputStream(targetStream)

                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }

                // 关闭文件描述符
                sourceFd.close()
                targetFd.close()

                true
            } catch (e: Exception) {
                Log.e(TAG, "Error copying file from '$sourcePath' to '$targetPath'", e)
                false
            }
        }

    /**
     * 验证存档名称是否有效
     * @param name 存档名称
     * @return 名称是否有效
     */
    private fun isValidArchiveName(name: String): Boolean {
        return name.isNotBlank() &&
                name.trim().length > 0 &&
                !name.contains("/") &&
                !name.contains("\\") &&
                name != "." &&
                name != ".."
    }

    /**
     * 更新元信息文件中的存档名称（如果需要的话）
     * @param fs 文件系统接口
     * @param metaFilePath 元信息文件路径
     * @param oldName 旧存档名称
     * @param newName 新存档名称
     * @return 是否更新成功
     */
    private suspend fun updateMetaInfoName(
        fs: IFileSystem,
        metaFilePath: String,
        oldName: String,
        newName: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 此处可以实现更新MetaInfo对象中的存档名称
            // 目前MetaInfo类似乎没有直接存储存档名称字段
            // 如果将来需要，可以在这里添加相关逻辑
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating meta info for archive rename", e)
            false
        }
    }
}
package tiiehenry.android.snapshotor.provider.filesystem

import android.content.Context
import tiiehenry.android.snapshotor.file.ICompressCallback
import tiiehenry.android.snapshotor.file.IFileCompressor
import tiiehenry.android.snapshotor.file.IFileSystem
import tiiehenry.android.snapshotor.fs.CompressorAlgorithms
import tiiehenry.android.snapshotor.provider.filesystem.compressors.CopyCompressor
import tiiehenry.android.snapshotor.provider.filesystem.compressors.ZipCompressor
import tiiehenry.android.snapshotor.provider.filesystem.compressors.ZstdCompressor
import tiiehenry.android.snapshotor.task.ITaskHandler
import java.io.File

class FileCompressor(val fs: IFileSystem, val context: Context) : IFileCompressor.Stub() {
    override fun supportedAlgorithms(): List<String?> {
        return mutableListOf(
            CompressorAlgorithms.ALGORITHM_ZSTD,
            CompressorAlgorithms.ALGORITHM_COPY,
            CompressorAlgorithms.ALGORITHM_ZIP
        )
    }

    override fun fileExtension(algorithm: String, type: String, file: String): String {
        when (algorithm) {
            CompressorAlgorithms.ALGORITHM_COPY -> {
                return when (type) {
                    "apk" -> "apk"
                    else -> File(file).extension
                }
            }

            CompressorAlgorithms.ALGORITHM_ZIP -> return "zip"
            CompressorAlgorithms.ALGORITHM_ZSTD -> return "zst"
            else -> return "unknown"
        }
    }

    override fun detectAlgorithm(file: String): String {
        if (file.endsWith(".apk")) {
            return CompressorAlgorithms.ALGORITHM_COPY
        }
        if (file.endsWith(".zip")) {
            return CompressorAlgorithms.ALGORITHM_ZIP
        }
        if (file.endsWith(".zst")) {
            return CompressorAlgorithms.ALGORITHM_ZSTD
        }
        return CompressorAlgorithms.ALGORITHM_ZIP
    }

    override fun compress(
        algorithm: String,
        dir: String,
        targetFile: String,
        excludes: List<String>,
        excludeFiles: List<String>,
        callback: ICompressCallback
    ): ITaskHandler {
        when (algorithm) {
            CompressorAlgorithms.ALGORITHM_COPY -> {
                return CopyCompressor.compress(
                    context,
                    fs,
                    dir,
                    targetFile,
                    excludes,
                    excludeFiles,
                    callback
                )
            }

            CompressorAlgorithms.ALGORITHM_ZIP -> {
                return ZipCompressor.compress(
                    context,
                    fs,
                    dir,
                    targetFile,
                    excludes,
                    excludeFiles,
                    callback
                )
            }

            CompressorAlgorithms.ALGORITHM_ZSTD -> {
                return ZstdCompressor.compress(
                    context,
                    fs,
                    dir,
                    targetFile,
                    excludes,
                    excludeFiles,
                    callback
                )
            }

            else -> return ZipCompressor.compress(
                context,
                fs,
                dir,
                targetFile,
                excludes,
                excludeFiles,
                callback
            )
        }
    }

    override fun checkFileValid(
        algorithm: String,
        path: String,
        size: Long,
        md5: String?
    ): Boolean {
        return fs.calculateSize(path) == size && fs.md5(path) == md5
    }

    override fun decompress(
        algorithm: String,
        file: String,
        targetDir: String,
        callback: ICompressCallback?
    ): ITaskHandler? {
        TODO("Not yet implemented")
    }
}
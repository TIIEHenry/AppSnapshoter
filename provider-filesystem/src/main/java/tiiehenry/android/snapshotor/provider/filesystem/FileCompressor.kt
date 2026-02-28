package tiiehenry.android.snapshotor.provider.filesystem

import android.content.Context
import tiiehenry.android.snapshotor.file.ICompressCallback
import tiiehenry.android.snapshotor.file.IFileCompressor
import tiiehenry.android.snapshotor.file.IFileSystem
import tiiehenry.android.snapshotor.fs.CompressorAlgorithms
import tiiehenry.android.snapshotor.provider.filesystem.compressors.TarCompressor
import tiiehenry.android.snapshotor.provider.filesystem.compressors.ZipCompressor
import tiiehenry.android.snapshotor.provider.filesystem.compressors.ZstdCompressor
import tiiehenry.android.snapshotor.provider.filesystem.decompressors.TarDecompressor
import tiiehenry.android.snapshotor.provider.filesystem.decompressors.ZipDecompressor
import tiiehenry.android.snapshotor.provider.filesystem.decompressors.ZstdDecompressor
import tiiehenry.android.snapshotor.task.ITaskHandler

class FileCompressor(val fs: IFileSystem, val context: Context) : IFileCompressor.Stub() {
    override fun supportedAlgorithms(): List<String?> {
        return mutableListOf(
            CompressorAlgorithms.ALGORITHM_ZSTD,
            CompressorAlgorithms.ALGORITHM_TAR,
            CompressorAlgorithms.ALGORITHM_ZIP
        )
    }

    override fun fileExtension(algorithm: String, type: String, file: String): String {
        when (algorithm) {
            CompressorAlgorithms.ALGORITHM_TAR -> return ".tar"
            CompressorAlgorithms.ALGORITHM_ZIP -> return ".zip"
            CompressorAlgorithms.ALGORITHM_ZSTD -> return ".tar.zst"
            else -> return ".unknown"
        }
    }

    override fun detectAlgorithm(file: String): String {
        if (file.endsWith(".apk")) {
            return CompressorAlgorithms.ALGORITHM_TAR
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

    override fun compressMultiple(
        algorithm: String,
        files: List<String>,
        targetFile: String,
        callback: ICompressCallback
    ): ITaskHandler {
        return when (algorithm) {
            CompressorAlgorithms.ALGORITHM_TAR -> {
                TarCompressor.compressMultiple(
                    context,
                    fs,
                    files,
                    targetFile,
                    callback
                )
            }

            CompressorAlgorithms.ALGORITHM_ZIP -> {
                ZipCompressor.compressMultiple(
                    context,
                    fs,
                    files,
                    targetFile,
                    callback
                )
            }

            CompressorAlgorithms.ALGORITHM_ZSTD -> {
                ZstdCompressor.compressMultiple(
                    context,
                    fs,
                    files,
                    targetFile,
                    callback
                )
            }

            else -> ZipCompressor.compressMultiple(
                context,
                fs,
                files,
                targetFile,
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
        return when (algorithm) {
            CompressorAlgorithms.ALGORITHM_ZSTD -> {
                ZstdDecompressor.decompress(fs, file, targetDir, callback)
            }

            CompressorAlgorithms.ALGORITHM_ZIP -> {
                ZipDecompressor.decompress(fs, file, targetDir, callback)
            }

            CompressorAlgorithms.ALGORITHM_TAR -> {
                TarDecompressor.decompress(fs, file, targetDir, callback)
            }

            else -> {
                // 默认尝试自动检测
                val detectedAlgorithm = detectAlgorithm(file)
                decompress(detectedAlgorithm, file, targetDir, callback)
            }
        }
    }
}
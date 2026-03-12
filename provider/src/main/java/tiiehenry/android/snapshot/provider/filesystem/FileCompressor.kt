package tiiehenry.android.snapshot.provider.filesystem

import android.content.Context
import tiiehenry.android.snapshot.file.ICompressCallback
import tiiehenry.android.snapshot.file.IFileCompressor
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.fs.CompressorAlgorithms
import tiiehenry.android.snapshot.provider.filesystem.compressors.tar.TarCompressor
import tiiehenry.android.snapshot.provider.filesystem.compressors.tar.TarDecompressor
import tiiehenry.android.snapshot.provider.filesystem.compressors.zstd.ZstdCompressor
import tiiehenry.android.snapshot.provider.filesystem.compressors.zstd.ZstdDecompressor
import tiiehenry.android.snapshot.task.ITaskHandler

class FileCompressor(val fs: IFileSystem, val context: Context) : IFileCompressor.Stub() {
    override fun supportedAlgorithms(): List<String?> {
        return mutableListOf(
            CompressorAlgorithms.ALGORITHM_ZSTD,
            CompressorAlgorithms.ALGORITHM_TAR,
        )
    }

    override fun fileExtension(algorithm: String, type: String, file: String): String {
        when (algorithm) {
            CompressorAlgorithms.ALGORITHM_ZSTD -> return ".tar.zst"
            else -> return ".tar"
        }
    }

    override fun detectAlgorithm(file: String): String {
        if (file.endsWith(".apk")) {
            return CompressorAlgorithms.ALGORITHM_TAR
        }
        if (file.endsWith(".zst")) {
            return CompressorAlgorithms.ALGORITHM_ZSTD
        }
        return CompressorAlgorithms.ALGORITHM_ZSTD
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

            else -> return TarCompressor.compress(
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

            CompressorAlgorithms.ALGORITHM_ZSTD -> {
                ZstdCompressor.compressMultiple(
                    context,
                    fs,
                    files,
                    targetFile,
                    callback
                )
            }

            else -> {
                TarCompressor.compressMultiple(
                    context,
                    fs,
                    files,
                    targetFile,
                    callback
                )
            }
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
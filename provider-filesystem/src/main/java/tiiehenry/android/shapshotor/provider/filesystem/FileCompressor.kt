package tiiehenry.android.shapshotor.provider.filesystem

import android.os.IBinder
import tiiehenry.android.shapshotor.file.ICompressCallback
import tiiehenry.android.shapshotor.file.IFileCompressor
import tiiehenry.android.shapshotor.file.IFileSystem
import tiiehenry.android.shapshotor.fs.CompressorAlgorithms
import tiiehenry.android.shapshotor.provider.filesystem.compressors.CopyCompressor
import tiiehenry.android.shapshotor.provider.filesystem.compressors.ZipCompressor
import tiiehenry.android.shapshotor.task.ITaskHandler
import java.io.File

class FileCompressor(val fs: IFileSystem) : IFileCompressor.Stub() {
    override fun supportedAlgorithms(): List<String?> {
        return mutableListOf(
            CompressorAlgorithms.ALGORITHM_COPY,
            CompressorAlgorithms.ALGORITHM_ZIP
        )
    }

    override fun fileExtension(algorithm: String, type: String, file: String): String {
        when (algorithm) {
            CompressorAlgorithms.ALGORITHM_COPY -> {
                return when (type) {
                    "apk" -> ".apk"
                    else -> File(file).extension
                }
            }

            CompressorAlgorithms.ALGORITHM_ZIP -> return ".zip"
            else -> return ".unknown"
        }
    }

    override fun detectAlgorithm(file: String): String {
        if (file.endsWith(".apk")) {
            return CompressorAlgorithms.ALGORITHM_COPY
        }
        return CompressorAlgorithms.ALGORITHM_ZIP
    }

    override fun compress(
        algorithm: String,
        dir: String,
        targetFile: String,
        excludes: List<String>,
        callback: ICompressCallback
    ): ITaskHandler {
        when (algorithm) {
            CompressorAlgorithms.ALGORITHM_COPY -> {
                return CopyCompressor.compress(dir, targetFile, excludes, callback)
            }

            else -> return ZipCompressor.compress(dir, targetFile, excludes, callback)
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

    override fun asBinder(): IBinder? {
        TODO("Not yet implemented")
    }
}
package tiiehenry.android.snapshot.provider.filesystem

import android.content.Context
import tiiehenry.android.snapshot.file.ICompressCallback
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.task.ITaskHandler

interface IAlgorithmCompressor {
    fun compress(
        context: Context,
        fileSystem: IFileSystem,
        dir: String,
        targetFile: String,
        excludes: List<String>,
        excludeFiles: List<String>,
        callback: ICompressCallback
    ): ITaskHandler

    fun compressMultiple(
        context: Context,
        fileSystem: IFileSystem,
        files: List<String>,
        targetFile: String,
        callback: ICompressCallback
    ): ITaskHandler

}

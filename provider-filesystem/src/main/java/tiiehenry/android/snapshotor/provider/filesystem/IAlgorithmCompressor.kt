package tiiehenry.android.snapshotor.provider.filesystem

import android.content.Context
import tiiehenry.android.snapshotor.file.ICompressCallback
import tiiehenry.android.snapshotor.file.IFileSystem
import tiiehenry.android.snapshotor.task.ITaskHandler

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

}

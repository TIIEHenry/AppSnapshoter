package tiiehenry.android.shapshotor.provider.filesystem

import tiiehenry.android.shapshotor.file.ICompressCallback
import tiiehenry.android.shapshotor.task.ITaskHandler

interface IAlgorithmCompressor {
    fun compress(
        dir: String,
        targetFile: String,
        excludes: List<String>,
        callback: ICompressCallback
    ): ITaskHandler

}

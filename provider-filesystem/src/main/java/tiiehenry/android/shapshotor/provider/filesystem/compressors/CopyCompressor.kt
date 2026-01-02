package tiiehenry.android.shapshotor.provider.filesystem.compressors

import tiiehenry.android.shapshotor.file.ICompressCallback
import tiiehenry.android.shapshotor.fs.CompressState
import tiiehenry.android.shapshotor.provider.filesystem.IAlgorithmCompressor
import tiiehenry.android.shapshotor.provider.filesystem.MD5Utils
import tiiehenry.android.shapshotor.task.ITaskHandler
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object CopyCompressor : IAlgorithmCompressor {
    override fun compress(
        dir: String,
        targetFile: String,
        excludes: List<String>,
        callback: ICompressCallback
    ): ITaskHandler {
        return object : ITaskHandler.Stub() {
            var state = CompressState.COMPRESS_STATE_NONE
            var isCancel = AtomicBoolean(false)
            override fun id(): String {
                return "copy:" + dir + ">" + targetFile
            }

            override fun state(): Int {
                return state
            }

            override fun start() {
                state = CompressState.COMPRESS_STATE_RUNNING
                doCompress(dir, targetFile, excludes, callback)
            }

            override fun cancel() {
                isCancel.set(true)
            }


        }
    }


    private fun doCompress(
        dir: String,
        targetFile: String,
        excludes: List<String>,
        callback: ICompressCallback
    ) {
        val sourceDir = File(dir)
        if (!sourceDir.exists()) {
            callback.onError("source not exists")
            return
        }
        val target = File(targetFile)
        if (target.exists()) {
            callback.onError("target exists")
            return
        }
        callback.onStart()
        if (sourceDir.isFile) {
            target.parentFile?.mkdirs()
            sourceDir.copyTo(target)
            val fileSize = target.length()
            if (sourceDir.length()!=fileSize){
                callback.onError("copy failed, length not eq")
                return
            }
            callback.onDone(fileSize, fileSize, MD5Utils.getFileMD5(sourceDir))
            return
        }
        throw IllegalStateException("source is not a file")
    }

}

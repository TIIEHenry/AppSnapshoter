package tiiehenry.android.compress.zstd

object TarJNI {

    init {
        System.loadLibrary("tar-jni")
    }

    /**
     * @param pipeFile 管道文件，注意不能直接在参数中指定管道文件，只能将管道文件作为stdin输入，pipeFile会重定向到stdin
     */
    external fun callCli(pipeFile: String, stdOut: String, stdErr: String, argv: Array<String>): Int
}
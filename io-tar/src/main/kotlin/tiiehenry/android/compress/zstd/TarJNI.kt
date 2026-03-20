package tiiehenry.android.compress.zstd

object TarJNI {

    init {
        System.loadLibrary("tar-jni")
    }

    external fun callCli(pipeFile: String,stdOut: String, stdErr: String, argv: Array<String>): Int
}
package tiiehenry.android.compress.zstd

object TarJNI {

    init {
        System.loadLibrary("tar-wrapper")
    }

    external fun callCli(stdOut: String, stdErr: String, argv: Array<String>): Int
}
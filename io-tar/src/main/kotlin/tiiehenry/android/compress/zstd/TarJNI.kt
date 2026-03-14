package tiiehenry.android.compress.zstd

object TarJNI {
    external fun callCli(stdOut: String, stdErr: String, argv: Array<String>): Int
}
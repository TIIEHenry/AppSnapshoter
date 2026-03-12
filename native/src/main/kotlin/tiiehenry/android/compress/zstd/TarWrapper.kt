package tiiehenry.android.compress.zstd

object TarWrapper {
    external fun callCli(stdOut: String, stdErr: String, argv: Array<String>): Int
}
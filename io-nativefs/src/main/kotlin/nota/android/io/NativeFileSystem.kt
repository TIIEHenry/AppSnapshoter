package nota.android.io

object NativeFileSystem {
    init {
        System.loadLibrary("native-filesystem")
    }

    external fun calculateTreeSize(path: String): Long
    external fun getUid(path: String): Int
    external fun getGid(path: String): Int
}
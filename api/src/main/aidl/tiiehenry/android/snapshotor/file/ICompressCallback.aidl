package tiiehenry.android.snapshotor.file;

interface ICompressCallback {
    void onStart();
    void onProgress(long bytesWritten, long kbPerS);
    void onDone(long originSize, long targetSize, String md5);
    void onError(String msg);
}

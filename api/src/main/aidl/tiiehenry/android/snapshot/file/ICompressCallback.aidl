package tiiehenry.android.snapshot.file;

interface ICompressCallback {
    void onStart();
    void onProgress(long bytesWritten, long kbPerS);
    void onDone(long originSize, long targetSize, String md5);
    void onError(String msg);
}

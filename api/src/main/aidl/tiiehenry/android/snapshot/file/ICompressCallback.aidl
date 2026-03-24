package tiiehenry.android.snapshot.file;

interface ICompressCallback {
    void onStart();
    void onProgress(long bytesWritten, long bytesPerS);
    void onDone(long originSize, long targetSize, String md5);
    void onError(String msg);
}

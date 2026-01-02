package tiiehenry.android.shapshotor.file;

interface ICompressCallback {
    void onStart();
    void onProgress(int progress, long kbPerS);
    void onDone(long originSize, long targetSize, String md5);
    void onError(String msg);
}

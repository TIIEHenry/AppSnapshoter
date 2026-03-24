package tiiehenry.android.snapshot.sync;

interface ISyncCallback {
    void onWait();
    void onProgress(int progress, long bytesPerS);
    void onDone();
    void onError(String msg);
}

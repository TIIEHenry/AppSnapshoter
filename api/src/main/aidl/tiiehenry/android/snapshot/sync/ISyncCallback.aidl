package tiiehenry.android.snapshot.sync;

interface ISyncCallback {
    void onWait();
    void onProgress(int progress, long kbPerS);
    void onDone();
    void onError(String msg);
}

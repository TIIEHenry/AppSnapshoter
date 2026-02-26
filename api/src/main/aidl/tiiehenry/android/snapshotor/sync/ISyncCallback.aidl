package tiiehenry.android.snapshotor.sync;

interface ISyncCallback {
    void onWait();
    void onProgress(int progress, long kbPerS);
    void onDone();
    void onError(String msg);
}

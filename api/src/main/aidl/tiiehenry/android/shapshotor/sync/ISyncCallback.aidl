package tiiehenry.android.shapshotor.sync;

interface ISyncCallback {
    void onWait();
    void onProgress(int progress, long kbPerS);
    void onDone();
    void onError(String msg);
}

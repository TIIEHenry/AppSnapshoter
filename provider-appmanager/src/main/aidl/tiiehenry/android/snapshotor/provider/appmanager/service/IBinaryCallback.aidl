package tiiehenry.android.snapshotor.provider.appmanager.service;

interface IBinaryCallback {
    void onProgress(long bytesWritten, long speed);
}

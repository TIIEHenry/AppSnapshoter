package tiiehenry.android.snapshot.provider.appmanager.service;

interface IBinaryCallback {
    void onProgress(long bytesWritten, long speed);
}

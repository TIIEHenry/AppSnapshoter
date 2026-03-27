package tiiehenry.android.snapshot.provider.service;

interface IBinaryCallback {
    void onProgress(long bytesWritten, long speed);
}

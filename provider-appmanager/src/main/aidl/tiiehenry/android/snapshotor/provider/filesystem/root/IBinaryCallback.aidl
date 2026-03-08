package tiiehenry.android.snapshotor.provider.filesystem.root;

interface IBinaryCallback {
    void onProgress(long bytesWritten, long speed);
}
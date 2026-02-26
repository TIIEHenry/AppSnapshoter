package tiiehenry.android.snapshotor.file;

interface IBinaryCallback {
    void onProgress(long bytesWritten, long speed);
}
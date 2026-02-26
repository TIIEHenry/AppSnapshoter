package tiiehenry.android.snapshotor.sync;

interface IRemoteDeviceCallback {
    void onSuccess();
    void onError(String msg);
}

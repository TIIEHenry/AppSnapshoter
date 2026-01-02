package tiiehenry.android.shapshotor.sync;

interface IRemoteDeviceCallback {
    void onSuccess();
    void onError(String msg);
}

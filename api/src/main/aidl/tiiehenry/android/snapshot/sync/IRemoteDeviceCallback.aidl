package tiiehenry.android.snapshot.sync;

interface IRemoteDeviceCallback {
    void onSuccess();
    void onError(String msg);
}

package tiiehenry.android.snapshotor.sync;

import tiiehenry.android.snapshotor.sync.IRemoteDevice;
import tiiehenry.android.snapshotor.sync.IRemoteDeviceCallback;
import tiiehenry.android.snapshotor.sync.ISyncCallback;
import tiiehenry.android.snapshotor.task.ITaskHandler;
import tiiehenry.android.snapshotor.file.IFileSystem;

interface IDataSyncer {
    IRemoteDevice getLocalDevice();
    List<IRemoteDevice> findRemoteDevices(boolean findOnNetwork);
    List<String> getPairedDevices();
    void requestPairDevice(in IRemoteDevice remoteDevice, in IRemoteDeviceCallback callback);
    boolean connectDevice(in IRemoteDevice remoteDevice);
    ITaskHandler sendFile(in IFileSystem fileSystem, String file, String path, in ISyncCallback callback);
    ITaskHandler receiveFile(in IFileSystem fileSystem, String path, String localPath, in ISyncCallback callback);
}
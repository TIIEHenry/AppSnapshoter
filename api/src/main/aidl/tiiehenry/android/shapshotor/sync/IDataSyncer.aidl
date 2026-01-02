package tiiehenry.android.shapshotor.sync;

import tiiehenry.android.shapshotor.sync.IRemoteDevice;
import tiiehenry.android.shapshotor.sync.IRemoteDeviceCallback;
import tiiehenry.android.shapshotor.sync.ISyncCallback;
import tiiehenry.android.shapshotor.task.ITaskHandler;
import tiiehenry.android.shapshotor.file.IFileSystem;

interface IDataSyncer {
    IRemoteDevice getLocalDevice();
    List<IRemoteDevice> findRemoteDevices(boolean findOnNetwork);
    List<IRemoteDevice> getPairedDevices();
    void requestPairDevice(in IRemoteDevice remoteDevice, in IRemoteDeviceCallback callback);
    boolean connectDevice(in IRemoteDevice remoteDevice);
    ITaskHandler sendFile(in IFileSystem fileSystem, String file, String path, in ISyncCallback callback);
    ITaskHandler receiveFile(in IFileSystem fileSystem, String path, String localPath, in ISyncCallback callback);
}

package tiiehenry.android.snapshotor.provider.filesystem.root.fsm;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.topjohnwu.superuser.ipc.RootService;

import java.util.concurrent.ConcurrentHashMap;

import tiiehenry.android.snapshotor.app.IServiceClient;
import tiiehenry.android.snapshotor.provider.filesystem.root.FileSystemRootService;
import tiiehenry.android.snapshotor.provider.filesystem.root.IFileSystemRootService;

public class FileSystemManagerRootServiceClient extends IServiceClient<IFileSystemRootService> {

    public String packageName;

    public FileSystemManagerRootServiceClient(String providerPkg) {
        super();
        this.packageName = providerPkg;
    }

    @Override
    @NonNull
    public String getLogTag() {
        return "FileSystemRootServiceClient";
    }


    @NonNull
    @Override
    public IFileSystemRootService asInterface(@NonNull IBinder iBinder) {
        return IFileSystemRootService.Stub.asInterface(iBinder);
    }

    @Override
    public Intent getIntent(@NonNull Context context) {
        return new Intent(context, FileSystemRootService.class);
    }

    @Nullable
    @Override
    public ServiceConnection doBind(@NonNull Context context, Intent intent, ServiceConnection conn) {
        RootService.bind(intent,/* executor,*/ conn);
        return conn;
    }

    @Override
    public void doUnbind(@NonNull Context context, @NonNull ServiceConnection connection) {
        RootService.unbind(connection);
    }

    public static final ConcurrentHashMap<String, FileSystemManagerRootServiceClient> instancesMap = new ConcurrentHashMap<>();


    /**
     * 如果继承了该类，需要在子类中需要重新实现INSTANCE
     *
     * @return
     */
    public static FileSystemManagerRootServiceClient getInstance() {
        return getInstance("tiiehenry.android.app.snapshotor");
    }

    public static FileSystemManagerRootServiceClient getInstance(String providerPkg) {
        return instancesMap.computeIfAbsent(providerPkg, FileSystemManagerRootServiceClient::new);
    }

    public static boolean freeInstance(String providerPkg) {
        return instancesMap.remove(providerPkg) != null;
    }
}
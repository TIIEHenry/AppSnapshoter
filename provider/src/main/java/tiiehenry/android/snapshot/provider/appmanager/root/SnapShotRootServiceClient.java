package tiiehenry.android.snapshot.provider.appmanager.root;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.topjohnwu.superuser.ipc.RootService;

import java.util.concurrent.ConcurrentHashMap;

import tiiehenry.android.snapshot.app.IServiceClient;
import tiiehenry.android.snapshot.provider.appmanager.service.ISnapShotRootService;

public class SnapShotRootServiceClient extends IServiceClient<ISnapShotRootService> {

    public String packageName;

    public SnapShotRootServiceClient(String providerPkg) {
        super();
        this.packageName = providerPkg;
    }

    @Override
    @NonNull
    public String getLogTag() {
        return "SnapShotRootServiceClient";
    }

    @NonNull
    @Override
    public ISnapShotRootService asInterface(@NonNull IBinder iBinder) {
        return ISnapShotRootService.Stub.asInterface(iBinder);
    }

    @Override
    public Intent getIntent(@NonNull Context context) {
        return new Intent(context, SnapshotRootService.class);
    }

    @Nullable
    @Override
    public ServiceConnection doBind(@NonNull Context context, Intent intent, ServiceConnection conn) {
        android.util.Log.i(getLogTag(), "doBind");
        RootService.bind(intent, executor, conn);
        return conn;
    }

    @Override
    public void doUnbind(@NonNull Context context, @NonNull ServiceConnection connection) {
        RootService.unbind(connection);
    }

    public static final ConcurrentHashMap<String, SnapShotRootServiceClient> instancesMap = new ConcurrentHashMap<>();

    /**
     * 如果继承了该类，需要在子类中需要重新实现INSTANCE
     *
     * @return
     */
    public static SnapShotRootServiceClient getInstance() {
        return getInstance("tiiehenry.android.app.snapshot");
    }

    public static SnapShotRootServiceClient getInstance(String providerPkg) {
        return instancesMap.computeIfAbsent(providerPkg, SnapShotRootServiceClient::new);
    }

    public static boolean freeInstance(String providerPkg) {
        return instancesMap.remove(providerPkg) != null;
    }
}

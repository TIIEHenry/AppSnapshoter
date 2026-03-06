package tiiehenry.android.snapshotor.provider.appmanager.root;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.topjohnwu.superuser.ipc.RootService;

import java.util.concurrent.ConcurrentHashMap;

import tiiehenry.android.snapshotor.app.IServiceClient;
import tiiehenry.android.snapshotor.provider.appmanager.service.IAppManageRootService;

public class AppManageRootServiceClient extends IServiceClient<IAppManageRootService> {

    public String packageName;

    public AppManageRootServiceClient(String providerPkg) {
        super();
        this.packageName = providerPkg;
    }

    @Override
    @NonNull
    public String getLogTag() {
        return "AppManageRootServiceClient";
    }


    @NonNull
    @Override
    public IAppManageRootService asInterface(@NonNull IBinder iBinder) {
        return IAppManageRootService.Stub.asInterface(iBinder);
    }

    @Override
    public Intent getIntent(@NonNull Context context) {
        return new Intent(context, AppManageRootService.class);
    }

    @Nullable
    @Override
    public ServiceConnection doBind(@NonNull Context context, Intent intent, ServiceConnection conn) {
        RootService.bind(intent, executor, conn);
        return conn;
    }

    @Override
    public void doUnbind(@NonNull Context context, @NonNull ServiceConnection connection) {
        RootService.unbind(connection);
    }

    public static final ConcurrentHashMap<String, AppManageRootServiceClient> instancesMap = new ConcurrentHashMap<>();


    /**
     * 如果继承了该类，需要在子类中需要重新实现INSTANCE
     *
     * @return
     */
    public static AppManageRootServiceClient getInstance() {
        return getInstance("tiiehenry.android.app.snapshotor");
    }

    public static AppManageRootServiceClient getInstance(String providerPkg) {
        return instancesMap.computeIfAbsent(providerPkg, AppManageRootServiceClient::new);
    }

    public static boolean freeInstance(String providerPkg) {
        return instancesMap.remove(providerPkg) != null;
    }
}
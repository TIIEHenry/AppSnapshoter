package tiiehenry.android.snapshotor.provider.appmanager.service;

import tiiehenry.android.snapshotor.app.AppPermission;
import tiiehenry.android.snapshotor.provider.appmanager.parcelables.BytesParcelable;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.UserInfo;
import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;

interface IAppManageRootService {
    void testConnection();
    ParcelFileDescriptor getInstalledAppInfos();
    ParcelFileDescriptor getInstalledAppStorages();
    List<UserInfo> getUsers();
    List<BytesParcelable> getPrivilegedConfiguredNetworks();
    int[] addNetworks(in List<BytesParcelable> configs);
    List<String> getPackageSourceDir(String packageName, int userId);
    
    // 应用信息获取方法
    PackageInfo getPackageInfo(String packageName, int flags, int userId);
    ApplicationInfo getApplicationInfo(String packageName, int flags, int userId);
    String loadLabel(String packageName, int userId);
    Bitmap loadIcon(String packageName, int userId);
    List<AppPermission> getPermissions(String packageName, int userId);
    void setAppPermission(String packageName, int userId, in AppPermission permission);
    void setAppPermissions(String packageName, int userId, in List<AppPermission> permissions);
    boolean isInstalled(String packageName, int userId);
    boolean installApk(String file, int userId);
    boolean installApks(in List<String> files, int userId);
    boolean uninstallApk(String packageName, int userId);
    void forceStopPackage(String packageName, int userId);
    void clearAppData(String packageName, int userId);
    void suspendPackage(String packageName, int userId);
    void unsuspendPackage(String packageName, int userId);
}
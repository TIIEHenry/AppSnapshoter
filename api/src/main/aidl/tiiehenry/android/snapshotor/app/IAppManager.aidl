package tiiehenry.android.snapshotor.app;

import android.content.pm.PackageInfo;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.os.UserHandle;
import tiiehenry.android.snapshotor.app.AppPermission;
import tiiehenry.android.snapshotor.app.UserInfoParcelable;

interface IAppManager {
    List<UserInfoParcelable> getUsers();
    List<String> getInstalledPackages(int flags, int userId);
    PackageInfo getPackageInfo(String packageName, int flags, int userId);
    ApplicationInfo getApplicationInfo(String packageName, int flags, int userId);
    String loadLabel(String packageName, int userId);
    Bitmap loadIcon(String packageName, int userId);
    String getDir(String packageName, int userId, int type);
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
    
    // 权限管理方法
    void grantRuntimePermission(String packageName, String permName, in UserHandle user);
    void revokeRuntimePermission(String packageName, String permName, in UserHandle user);
    int getPermissionFlags(String packageName, String permName, in UserHandle user);
    void updatePermissionFlags(String packageName, String permName, in UserHandle user, int flagMask, int flagValues);
    
    // AppOps管理方法
    int getPackageUid(String packageName, int userId);
    UserHandle getUserHandle(int userId);
    void setOpsMode(int code, int uid, String packageName, int mode);
    void resetAppOps(int userId, String packageName);

    // SSAID管理方法
    String getPackageSsaidAsUser(String packageName, int uid, int userId);
    void setPackageSsaidAsUser(String packageName, int userId, String ssaid);

    // 检查应用是否正在运行
    boolean isPackageRunning(String packageName, int userId);
}

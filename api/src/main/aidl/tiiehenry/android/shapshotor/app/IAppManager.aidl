package tiiehenry.android.shapshotor.app;

import android.content.pm.PackageInfo;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import tiiehenry.android.shapshotor.app.AppPermission;

interface IAppManager {
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
    boolean uninstallApk(String packageName, int userId);
}

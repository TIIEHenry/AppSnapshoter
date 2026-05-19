package tiiehenry.android.snapshot.app;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;

import java.util.List;

/**
 * 包管理接口 - 负责包查询、安装/卸载、进程控制、应用启动
 */
public interface IPackageManager {

    // ========== 用户与包查询 ==========
    List<UserInfoHide> getUsers();

    List<String> getInstalledPackages(int flags, int userId);

    PackageInfo getPackageInfo(String packageName, int flags, int userId);

    ApplicationInfo getApplicationInfo(String packageName, int flags, int userId);

    String loadLabel(String packageName, int userId);

    Bitmap loadIcon(String packageName, int userId);

    String getDir(String packageName, int userId, int type);

    boolean isInstalled(String packageName, int userId);

    // ========== 安装/卸载 ==========
    boolean installApk(String file, int userId);

    boolean installApks(List<String> files, int userId);

    boolean uninstallApk(String packageName, int userId);

    // ========== 生命周期控制 ==========
    void forceStopPackage(String packageName, int userId);

    void clearAppData(String packageName, int userId);

    void suspendPackage(String packageName, int userId);

    void unsuspendPackage(String packageName, int userId);

    // ========== 运行状态 ==========
    boolean isPackageRunning(String packageName, int userId);

    boolean launchApp(String packageName, int userId);
}

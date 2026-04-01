package tiiehenry.android.snapshot.provider.service;

import tiiehenry.android.snapshot.app.AppInfo;
import tiiehenry.android.snapshot.app.AppStorage;
import tiiehenry.android.snapshot.app.AppPermission;
import tiiehenry.android.snapshot.provider.service.bean.StatFsResult;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.UserInfo;
import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;

interface ISnapShotRootService {

    // ==================== 连接测试 ====================
    boolean testConnection();

    // ==================== 应用管理方法 ====================
    List<AppInfo> getInstalledAppInfos();
    List<AppStorage> getInstalledAppStorages();
    List<UserInfo> getUsers();
    List<String> getPackageSourceDir(String packageName, int userId);

    // 应用信息获取方法
    PackageInfo getPackageInfo(String packageName, int flags, int userId);
    ApplicationInfo getApplicationInfo(String packageName, int flags, int userId);
    String loadLabel(String packageName, int userId);
    Bitmap loadIcon(String packageName, int userId);
    boolean isInstalled(String packageName, int userId);
    boolean installApk(String file, int userId);
    boolean installApks(in List<String> files, int userId);
    boolean uninstallApk(String packageName, int userId);
    boolean forceStopPackage(String packageName, int userId);
    boolean clearAppData(String packageName, int userId);
    boolean suspendPackage(String packageName, int userId);
    boolean unsuspendPackage(String packageName, int userId);

    // 权限管理方法
    List<AppPermission> getPermissions(String packageName, int userId);
    boolean setAppPermission(String packageName, int userId, in AppPermission permission);
    boolean setAppPermissions(String packageName, int userId, in List<AppPermission> permissions);
    int grantRuntimePermission(String packageName, String permName, in UserHandle user);
    int revokeRuntimePermission(String packageName, String permName, in UserHandle user);
    int getPermissionFlags(String packageName, String permName, in UserHandle user);
    boolean updatePermissionFlags(String packageName, String permName, in UserHandle user, int flagMask, int flagValues);

    // AppOps 管理方法
    int getPackageUid(String packageName, int userId);
    UserHandle getUserHandle(int userId);
    boolean setOpsMode(int code, int uid, String packageName, int mode);
    boolean resetAppOps(int userId, String packageName);

    // SSAID 管理方法
    String getPackageSsaidAsUser(String packageName, int uid, int userId);
    boolean setPackageSsaidAsUser(String packageName, int uid, int userId, String ssaid);

    // 检查应用是否正在运行
    boolean isPackageRunning(String packageName, int userId);

    // 用命令行方式启动应用
    boolean launchApp(String packageName, int userId);

    // ==================== 文件系统方法 ====================
    StatFsResult readStatFs(String path);
    ParcelFileDescriptor readText(String path);
    boolean writeText(String path, in ParcelFileDescriptor pfd);
    long calculateTreeSize(String path);
    int callTarCli(String pipeFile, String stdOut, String stdErr, in String[] argv);
    boolean mkdirs(String path);
    boolean exists(String path);
    int fileType(String path);
    boolean deleteRecursively(String path);
    boolean copyRecursively(String source, String target, boolean overwrite);
    long getLastModifiedTime(String path);
    boolean setLastModifiedTime(String path, long time);
    int getUid(String path);
    boolean setUid(String path, int uid);
    int getGid(String path);
    boolean setGid(String path, int gid);
    ParcelFileDescriptor openFile(String path, int mode);
    String md5(String file);
    boolean extractTar(String tarFifo, String targetDir);
}

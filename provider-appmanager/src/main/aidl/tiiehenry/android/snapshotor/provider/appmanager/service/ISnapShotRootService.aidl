package tiiehenry.android.snapshotor.provider.appmanager.service;

import tiiehenry.android.snapshotor.app.AppPermission;
import tiiehenry.android.snapshotor.provider.appmanager.parcelables.BytesParcelable;
import tiiehenry.android.snapshotor.provider.appmanager.parcelables.StatFsParcelable;
import tiiehenry.android.snapshotor.provider.appmanager.parcelables.FilePathParcelable;
import tiiehenry.android.snapshotor.provider.appmanager.service.IBinaryCallback;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.UserInfo;
import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;

interface ISnapShotRootService {

    // ==================== 连接测试 ====================
    void testConnection();

    // ==================== 应用管理方法 ====================
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
    void setPackageSsaidAsUser(String packageName, int uid, int userId, String ssaid);

    // 检查应用是否正在运行
    boolean isPackageRunning(String packageName, int userId);

    // ==================== 文件系统方法 ====================
    StatFsParcelable readStatFs(String path);
    List<FilePathParcelable> listFilePaths(String path, boolean listFiles, boolean listDirs);
    ParcelFileDescriptor readText(String path);
    void writeText(String path, in ParcelFileDescriptor pfd);
    long calculateTreeSize(String path);
    int callTarCli(String stdOut, String stdErr, in String[] argv);
    String compress(int level, String inputPath, String outputPath, IBinaryCallback callback);
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

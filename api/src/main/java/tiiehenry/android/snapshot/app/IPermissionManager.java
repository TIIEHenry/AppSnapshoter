package tiiehenry.android.snapshot.app;

import android.os.UserHandle;

import java.util.List;

/**
 * 权限管理接口 - 负责权限管理、AppOps、SSAID
 */
public interface IPermissionManager {

    // ========== 权限查询与设置 ==========
    List<AppPermission> getPermissions(String packageName, int userId);

    void setAppPermission(String packageName, int userId, AppPermission permission);

    void setAppPermissions(String packageName, int userId, List<AppPermission> permissions);

    void grantRuntimePermission(String packageName, String permName, UserHandle user);

    void revokeRuntimePermission(String packageName, String permName, UserHandle user);

    int getPermissionFlags(String packageName, String permName, UserHandle user);

    void updatePermissionFlags(String packageName, String permName, UserHandle user, int flagMask, int flagValues);

    // ========== AppOps 管理 ==========
    int getPackageUid(String packageName, int userId);

    UserHandle getUserHandle(int userId);

    void setOpsMode(int code, int uid, String packageName, int mode);

    void resetAppOps(int userId, String packageName);

    // ========== SSAID 管理 ==========
    String getPackageSsaidAsUser(String packageName, int uid, int userId);

    void setPackageSsaidAsUser(String packageName, int userId, String ssaid);
}

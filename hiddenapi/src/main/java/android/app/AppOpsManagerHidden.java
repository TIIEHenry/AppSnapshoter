package android.app;

import java.util.List;

import dev.rikka.tools.refine.RefineAs;

/**
 * @see <a href="https://cs.android.com/android/platform/superproject/+/android-7.0.0_r1:frameworks/base/core/java/android/app/AppOpsManager.java">AppOpsManager.java</a>
 */
@RefineAs(AppOpsManager.class)
public class AppOpsManagerHidden {
    public static final int OP_NONE = -1;
    public static final int _NUM_OP = 64;

    public static int permissionToOpCode(String permission) {
        throw new RuntimeException("Stub!");
    }

    public void setMode(int code, int uid, String packageName, int mode) {
        throw new RuntimeException("Stub!");
    }

}

package android.app;

import android.app.ActivityManager.RunningAppProcessInfo;

import java.util.List;

import dev.rikka.tools.refine.RefineAs;

/**
 * Hidden methods of ActivityManager.
 * @see <a href="https://cs.android.com/android/platform/superproject/+/android-8.0.0_r51:frameworks/base/core/java/android/app/ActivityManager.java">ActivityManager.java</a>
 */
@RefineAs(ActivityManager.class)
public class ActivityManagerHidden {
    /**
     * Returns a list of application processes that are running on the device.
     * 
     * Note: This method is only intended for debugging or building a user-facing
     * process management UI. On Android 5.0+ (API 21+), this method returns a limited
     * list of processes when called by non-system apps. However, when called from
     * a root service with system privileges, it returns the full list.
     *
     * @return Returns a list of RunningAppProcessInfo records, or null if there are no
     * running processes (it will not return an empty list).
     */
    public List<RunningAppProcessInfo> getRunningAppProcesses() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Returns a list of application processes that are running on the device, 
     * for the specified user.
     *
     * @param userId The user id to query running processes for
     * @return Returns a list of RunningAppProcessInfo records, or null if there are no
     * running processes.
     */
    public List<RunningAppProcessInfo> getRunningAppProcessesAsUser(int userId) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Force stop a package for a specific user.
     *
     * @param packageName The name of the package to stop
     * @param userId The user id
     */
    public void forceStopPackageAsUser(String packageName, int userId) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Force stop a package for all users.
     *
     * @param packageName The name of the package to stop
     */
    public void forceStopPackage(String packageName) {
        throw new RuntimeException("Stub!");
    }
}

package tiiehenry.android.snapshot.provider.service.handler

import android.os.Build
import android.os.HandlerThread
import android.os.Process
import android.os.RemoteException
import com.android.providers.settings.SettingsState
import com.android.providers.settings.SettingsStateApi26
import com.android.providers.settings.SettingsStateApi31
import tiiehenry.android.snapshot.provider.appmanager.util.LogHelper
import java.io.File

class SsaidManagementHandler {

    fun getPackageSsaidAsUser(packageName: String, uid: Int, userId: Int): String? {
        return try {
            val settingsState = getSettingsState(userId)
            settingsState.getSettingLocked(getSsaidName(packageName, uid))?.value
        } catch (e: Exception) {
            throw RemoteException("Failed to get ssaid for: $packageName: ${e.message}")
        }
    }

    fun setPackageSsaidAsUser(
        packageName: String,
        uid: Int,
        userId: Int,
        ssaid: String
    ): Boolean {
        return try {
            val settingsState = getSettingsState(userId)
            settingsState.insertSettingLocked(
                getSsaidName(packageName, uid),
                ssaid,
                null,
                true,
                packageName
            )
            LogHelper.i(
                "SsaidManagement",
                "setPackageSsaidAsUser",
                "Successfully set ssaid for $packageName"
            )
            true
        } catch (e: Exception) {
            LogHelper.e(
                "SsaidManagement",
                "setPackageSsaidAsUser",
                "Failed to set ssaid for $packageName: ${e.message}"
            )
            false
        }
    }

    private fun getSettingsState(userId: Int): SettingsState {
        val lock = Object()
        val thread = HandlerThread("ssaid_handler", Process.THREAD_PRIORITY_BACKGROUND)
        thread.start()
        val file = File("/data/system/users/$userId/settings_ssaid.xml")
        val key = SettingsState.makeKey(SettingsState.SETTINGS_TYPE_SSAID, userId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SettingsStateApi31(
                lock,
                file,
                key,
                SettingsState.MAX_BYTES_PER_APP_PACKAGE_UNLIMITED,
                thread.looper
            )
        } else {
            SettingsStateApi26(
                lock,
                file,
                key,
                SettingsState.MAX_BYTES_PER_APP_PACKAGE_UNLIMITED,
                thread.looper
            )
        }
    }

    private fun getSsaidName(packageName: String, uid: Int): String {
        return if (packageName == SettingsState.SYSTEM_PACKAGE_NAME) "userkey" else uid.toString()
    }
}

package tiiehenry.android.app.snapshot.main.settings

import android.util.Log
import com.alibaba.fastjson2.JSON
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.app.AppInfo
import java.io.File

/**
 * 忽略应用配置管理类
 * 以 packageName@userId 复合键保存忽略列表
 */
object IgnoreAppsConfig {
    private const val TAG = "IgnoreAppsConfig"
    private const val IGNORED_APPS_FILE_NAME = "ignored_apps.json"

    private var cachedIgnoredKeys: List<String>? = null

    fun appKey(packageName: String, userId: Int): String = "$packageName@$userId"

    private fun normalizeKey(key: String): String {
        return if (key.contains('@')) key else appKey(key, 0)
    }

    private fun getIgnoredAppsFilePath(): String {
        return File(SnapshotApp.Companion.getInstance().globalRootPath, IGNORED_APPS_FILE_NAME).absolutePath
    }

    private fun loadFromFile(): List<String> {
        return try {
            val file = File(getIgnoredAppsFilePath())
            if (!file.exists()) {
                emptyList()
            } else {
                val jsonString = file.readText()
                (JSON.parseArray(jsonString, String::class.java) ?: emptyList())
                    .map { normalizeKey(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ignored apps from file", e)
            emptyList()
        }
    }

    fun getIgnoredAppKeys(): List<String> {
        if (cachedIgnoredKeys == null) {
            cachedIgnoredKeys = loadFromFile()
        }
        return cachedIgnoredKeys!!
    }

    /** @deprecated 使用 [getIgnoredAppKeys] */
    @Deprecated("Use getIgnoredAppKeys", ReplaceWith("getIgnoredAppKeys()"))
    fun getIgnoredPackageNames(): List<String> = getIgnoredAppKeys()

    fun saveIgnoredAppKeys(keys: List<String>) {
        try {
            val normalized = keys.map { normalizeKey(it) }.distinct()
            val jsonString = JSON.toJSONString(normalized)
            val file = File(getIgnoredAppsFilePath())
            file.parentFile?.mkdirs()
            file.writeText(jsonString)
            cachedIgnoredKeys = normalized
            Log.d(TAG, "Saved ${normalized.size} ignored apps to file: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save ignored apps to file", e)
        }
    }

    fun clearCache() {
        cachedIgnoredKeys = null
    }

    fun addIgnoredApp(appInfo: AppInfo) {
        val key = appKey(appInfo.packageName, appInfo.userId)
        val currentList = getIgnoredAppKeys().toMutableList()
        if (currentList.contains(key)) {
            Log.d(TAG, "App $key is already ignored")
            return
        }
        currentList.add(key)
        saveIgnoredAppKeys(currentList)
        Log.d(TAG, "Added $key to ignored apps")
    }

    fun removeIgnoredApp(packageName: String, userId: Int) {
        val key = appKey(packageName, userId)
        val currentList = getIgnoredAppKeys().toMutableList()
        currentList.remove(key)
        if (userId == 0) {
            currentList.remove(packageName)
        }
        saveIgnoredAppKeys(currentList)
        Log.d(TAG, "Removed $key from ignored apps")
    }

    fun isIgnored(packageName: String, userId: Int): Boolean {
        val keys = getIgnoredAppKeys()
        return appKey(packageName, userId) in keys
    }

    fun isIgnored(appInfo: AppInfo): Boolean {
        return isIgnored(appInfo.packageName, appInfo.userId)
    }

    fun filterIgnoredApps(apps: List<AppInfo>): List<AppInfo> {
        val ignoredSet = getIgnoredAppKeys().toSet()
        return apps.filter { appKey(it.packageName, it.userId) !in ignoredSet }
    }
}

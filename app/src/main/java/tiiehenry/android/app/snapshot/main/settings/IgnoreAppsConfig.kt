package tiiehenry.android.app.snapshot.main.settings

import android.util.Log
import com.alibaba.fastjson2.JSON
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.app.AppInfo
import java.io.File

/**
 * 忽略应用配置管理类
 * 以 packageName@userId 复合键保存忽略列表
 *
 * 加载有三种状态：未加载、成功（含空列表）、失败。
 * 文件不存在是成功的空列表。文件在但读盘或 JSON 解析失败（含 parseArray 为 null）是失败，
 * 失败结果不进入缓存，也不写回磁盘。
 */
object IgnoreAppsConfig {
    private const val TAG = "IgnoreAppsConfig"
    private const val IGNORED_APPS_FILE_NAME = "ignored_apps.json"

    private enum class LoadState { UNLOADED, LOADED, FAILED }

    private var loadState = LoadState.UNLOADED
    private var cachedIgnoredKeys: List<String>? = null

    /** 单测注入忽略列表文件；生产路径仍走 [SnapshotApp.globalRootPath]。 */
    internal var ignoredAppsFileOverride: File? = null

    /** 单测注入读盘动作。返回前抛错即视为读失败。 */
    internal var ignoredAppsTextReader: ((File) -> String)? = null

    fun appKey(packageName: String, userId: Int): String = "$packageName@$userId"

    private fun normalizeKey(key: String): String {
        return if (key.contains('@')) key else appKey(key, 0)
    }

    private fun ignoredAppsFile(): File {
        return ignoredAppsFileOverride
            ?: File(SnapshotApp.getInstance().globalRootPath, IGNORED_APPS_FILE_NAME)
    }

    private fun readIgnoredText(file: File): String {
        val reader = ignoredAppsTextReader
        if (reader != null) return reader(file)
        return file.readText()
    }

    /** @return null 表示失败；空列表表示成功读到的空列表。 */
    private fun loadFromFile(): List<String>? {
        return try {
            val file = ignoredAppsFile()
            if (!file.exists()) {
                emptyList()
            } else {
                val parsed = JSON.parseArray(readIgnoredText(file), String::class.java) ?: return null
                parsed.map { normalizeKey(it) }
            }
        } catch (e: Exception) {
            logError("Failed to load ignored apps from file", e)
            null
        }
    }

    private fun reloadUnlessLoaded() {
        if (loadState == LoadState.LOADED) return
        val loaded = loadFromFile()
        if (loaded == null) {
            cachedIgnoredKeys = null
            loadState = LoadState.FAILED
        } else {
            cachedIgnoredKeys = loaded
            loadState = LoadState.LOADED
        }
    }

    private fun hasLoadedList(): Boolean {
        return loadState == LoadState.LOADED && cachedIgnoredKeys != null
    }

    fun getIgnoredAppKeys(): List<String> {
        reloadUnlessLoaded()
        return cachedIgnoredKeys ?: emptyList()
    }

    /** @deprecated 使用 [getIgnoredAppKeys] */
    @Deprecated("Use getIgnoredAppKeys", ReplaceWith("getIgnoredAppKeys()"))
    fun getIgnoredPackageNames(): List<String> = getIgnoredAppKeys()

    fun saveIgnoredAppKeys(keys: List<String>) {
        if (!hasLoadedList()) return
        try {
            val normalized = keys.map { normalizeKey(it) }.distinct()
            val jsonString = JSON.toJSONString(normalized)
            val file = ignoredAppsFile()
            file.parentFile?.mkdirs()
            file.writeText(jsonString)
            cachedIgnoredKeys = normalized
            logDebug("Saved ${normalized.size} ignored apps to file: ${file.absolutePath}")
        } catch (e: Exception) {
            logError("Failed to save ignored apps to file", e)
        }
    }

    fun clearCache() {
        cachedIgnoredKeys = null
        loadState = LoadState.UNLOADED
    }

    fun addIgnoredApp(appInfo: AppInfo) {
        reloadUnlessLoaded()
        if (!hasLoadedList()) return
        val key = appKey(appInfo.packageName, appInfo.userId)
        val currentList = cachedIgnoredKeys.orEmpty().toMutableList()
        if (currentList.contains(key)) {
            logDebug("App $key is already ignored")
            return
        }
        currentList.add(key)
        saveIgnoredAppKeys(currentList)
        logDebug("Added $key to ignored apps")
    }

    fun removeIgnoredApp(packageName: String, userId: Int) {
        reloadUnlessLoaded()
        if (!hasLoadedList()) return
        val key = appKey(packageName, userId)
        val currentList = cachedIgnoredKeys.orEmpty().toMutableList()
        currentList.remove(key)
        if (userId == 0) {
            currentList.remove(packageName)
        }
        saveIgnoredAppKeys(currentList)
        logDebug("Removed $key from ignored apps")
    }

    fun isIgnored(packageName: String, userId: Int): Boolean {
        reloadUnlessLoaded()
        if (!hasLoadedList()) return true
        return appKey(packageName, userId) in cachedIgnoredKeys.orEmpty()
    }

    fun isIgnored(appInfo: AppInfo): Boolean {
        return isIgnored(appInfo.packageName, appInfo.userId)
    }

    fun filterIgnoredApps(apps: List<AppInfo>): List<AppInfo> {
        reloadUnlessLoaded()
        val keys = cachedIgnoredKeys
        if (!hasLoadedList() || keys == null) {
            return emptyList()
        }
        val ignoredSet = keys.toSet()
        return apps.filter { appKey(it.packageName, it.userId) !in ignoredSet }
    }

    private fun logDebug(message: String) {
        try {
            Log.d(TAG, message)
        } catch (_: RuntimeException) {
            // android.jar 的 Log 在 JVM 单测里会抛 Stub。
        }
    }

    private fun logError(message: String, error: Exception) {
        try {
            Log.e(TAG, message, error)
        } catch (_: RuntimeException) {
            // android.jar 的 Log 在 JVM 单测里会抛 Stub。
        }
    }
}

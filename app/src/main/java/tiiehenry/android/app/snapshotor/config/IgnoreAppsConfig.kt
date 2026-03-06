package tiiehenry.android.app.snapshotor.config

import android.util.Log
import com.alibaba.fastjson2.JSON
import tiiehenry.android.app.snapshotor.SnapShotApp
import tiiehenry.android.app.snapshotor.app.AppInfo
import java.io.File

/**
 * 忽略应用配置管理类
 * 用于保存和管理用户选择忽略的应用列表
 * 只保存 packageName 列表到 JSON 文件
 */
object IgnoreAppsConfig {
    private const val TAG = "IgnoreAppsConfig"
    private const val IGNORED_APPS_FILE_NAME = "ignored_apps.json"

    // 全局缓存的忽略应用列表
    private var cachedIgnoredPackages: List<String>? = null

    /**
     * 获取忽略应用列表文件路径
     */
    private fun getIgnoredAppsFilePath(): String {
        return File(SnapShotApp.getInstance().defaultRootPath, IGNORED_APPS_FILE_NAME).absolutePath
    }

    /**
     * 从文件读取忽略的 packageName 列表
     */
    private fun loadFromFile(): List<String> {
        return try {
            val file = File(getIgnoredAppsFilePath())
            if (!file.exists()) {
                emptyList()
            } else {
                val jsonString = file.readText()
                JSON.parseArray(jsonString, String::class.java) ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ignored apps from file", e)
            emptyList()
        }
    }

    /**
     * 获取所有忽略的 packageName 列表（使用缓存）
     */
    fun getIgnoredPackageNames(): List<String> {
        if (cachedIgnoredPackages == null) {
            cachedIgnoredPackages = loadFromFile()
        }
        return cachedIgnoredPackages!!
    }

    /**
     * 保存忽略的 packageName 列表到 JSON 文件并更新缓存
     */
    fun saveIgnoredPackageNames(packageNames: List<String>) {
        try {
            val jsonString = JSON.toJSONString(packageNames)
            val file = File(getIgnoredAppsFilePath())
            file.parentFile?.mkdirs()
            file.writeText(jsonString)
            // 更新缓存
            cachedIgnoredPackages = packageNames
            Log.d(TAG, "Saved ${packageNames.size} ignored apps to file: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save ignored apps to file", e)
        }
    }

    /**
     * 清除缓存，下次读取时重新加载
     */
    fun clearCache() {
        cachedIgnoredPackages = null
    }

    /**
     * 添加应用到忽略列表
     */
    fun addIgnoredApp(appInfo: AppInfo) {
        val currentList = getIgnoredPackageNames().toMutableList()

        // 检查是否已存在
        if (currentList.contains(appInfo.packageName)) {
            Log.d(TAG, "App ${appInfo.packageName} is already ignored")
            return
        }

        currentList.add(appInfo.packageName)
        saveIgnoredPackageNames(currentList)
        Log.d(TAG, "Added ${appInfo.packageName} to ignored apps")
    }

    /**
     * 从忽略列表中移除应用
     */
    fun removeIgnoredApp(packageName: String, userId: Int) {
        val currentList = getIgnoredPackageNames().toMutableList()
        currentList.remove(packageName)
        saveIgnoredPackageNames(currentList)
        Log.d(TAG, "Removed $packageName from ignored apps")
    }

    /**
     * 检查应用是否在忽略列表中
     */
    fun isIgnored(packageName: String, userId: Int): Boolean {
        return getIgnoredPackageNames().contains(packageName)
    }

    /**
     * 检查应用是否在忽略列表中（AppInfo 版本）
     */
    fun isIgnored(appInfo: AppInfo): Boolean {
        return isIgnored(appInfo.packageName, appInfo.userId)
    }

    /**
     * 过滤掉已忽略的应用
     */
    fun filterIgnoredApps(apps: List<AppInfo>): List<AppInfo> {
        val ignoredSet = getIgnoredPackageNames().toSet()
        return apps.filter { it.packageName !in ignoredSet }
    }
}

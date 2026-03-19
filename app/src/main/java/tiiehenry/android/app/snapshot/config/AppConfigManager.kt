package tiiehenry.android.app.snapshot.config

import java.util.concurrent.ConcurrentHashMap

/**
 * 全局应用配置管理器
 * 负责管理和复用 AppConfig 实例，避免重复创建
 */
class AppConfigManager private constructor() {
    
    // 使用 ConcurrentHashMap 保证线程安全
    private val configCache = ConcurrentHashMap<String, AppConfig>()
    
    /**
     * 获取指定包名的应用配置
     * 如果缓存中不存在则创建新实例并缓存
     * 
     * @param packageName 应用包名
     * @return AppConfig 应用配置实例
     */
    fun getConfig(packageName: String): AppConfig {
        return configCache.getOrPut(packageName) {
            AppConfig(packageName)
        }
    }
    
    /**
     * 刷新指定应用的配置
     * 重新从文件加载配置并更新缓存
     * 
     * @param packageName 应用包名
     */
    fun refreshConfig(packageName: String) {
        configCache[packageName]?.load()
    }
    
    /**
     * 清除指定应用的配置缓存
     * 
     * @param packageName 应用包名
     */
    fun removeConfig(packageName: String) {
        configCache.remove(packageName)
    }
    
    /**
     * 清除所有配置缓存
     */
    fun clearAllConfigs() {
        configCache.clear()
    }
    
    /**
     * 获取当前缓存的配置数量
     */
    fun getConfigCount(): Int {
        return configCache.size
    }
    
    companion object {
        @Volatile
        private var instance: AppConfigManager? = null
        
        /**
         * 获取单例实例
         */
        fun getInstance(): AppConfigManager {
            return instance ?: synchronized(this) {
                instance ?: AppConfigManager().also {
                    instance = it
                }
            }
        }
    }
}

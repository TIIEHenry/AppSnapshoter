package tiiehenry.android.app.snapshotor.utils

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONReader
import com.alibaba.fastjson2.JSONWriter
import java.io.File

/**
 * JSON 工具类
 * 封装 FastJSON 的常用操作
 */
object JsonUtils {
    
    /**
     * 将对象转换为 JSON 字符串
     * @param obj 要转换的对象
     * @param prettyFormat 是否格式化输出（美化）
     * @return JSON 字符串
     */
    fun toJsonString(obj: Any?, prettyFormat: Boolean = false): String {
        return if (prettyFormat) {
            JSON.toJSONString(obj, JSONWriter.Feature.PrettyFormat)
        } else {
            JSON.toJSONString(obj)
        }
    }
    
    /**
     * 将 JSON 字符串解析为对象
     * @param json JSON 字符串
     * @param clazz 目标类型
     * @return 解析后的对象，解析失败返回 null
     */
    fun <T> parseObject(json: String?, clazz: Class<T>): T? {
        if (json.isNullOrEmpty()) return null
        return try {
            JSON.parseObject(json, clazz)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 将 JSON 字符串解析为对象列表
     * @param json JSON 字符串
     * @param clazz 列表元素类型
     * @return 解析后的列表，解析失败返回空列表
     */
    fun <T> parseArray(json: String?, clazz: Class<T>): List<T> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            JSON.parseArray(json, clazz) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * 从文件读取并解析 JSON
     * @param file JSON 文件
     * @param clazz 目标类型
     * @return 解析后的对象，解析失败返回 null
     */
    fun <T> parseFromFile(file: File, clazz: Class<T>): T? {
        if (!file.exists() || !file.isFile) return null
        return try {
            val json = file.readText()
            parseObject(json, clazz)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 从文件路径读取并解析 JSON
     * @param filePath JSON 文件路径
     * @param clazz 目标类型
     * @return 解析后的对象，解析失败返回 null
     */
    fun <T> parseFromFile(filePath: String, clazz: Class<T>): T? {
        return parseFromFile(File(filePath), clazz)
    }
    
    /**
     * 将对象写入 JSON 文件
     * @param obj 要写入的对象
     * @param file 目标文件
     * @param prettyFormat 是否格式化输出（美化）
     * @return 是否写入成功
     */
    fun writeToFile(obj: Any?, file: File, prettyFormat: Boolean = true): Boolean {
        return try {
            // 确保父目录存在
            file.parentFile?.let { parent ->
                if (!parent.exists()) {
                    parent.mkdirs()
                }
            }
            
            val json = toJsonString(obj, prettyFormat)
            file.writeText(json)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 将对象写入 JSON 文件
     * @param obj 要写入的对象
     * @param filePath 目标文件路径
     * @param prettyFormat 是否格式化输出（美化）
     * @return 是否写入成功
     */
    fun writeToFile(obj: Any?, filePath: String, prettyFormat: Boolean = true): Boolean {
        return writeToFile(obj, File(filePath), prettyFormat)
    }
    
    /**
     * 深度复制对象（通过 JSON 序列化/反序列化）
     * @param obj 要复制的对象
     * @param clazz 目标类型
     * @return 复制后的对象
     */
    fun <T> deepCopy(obj: T, clazz: Class<T>): T? {
        return try {
            val json = toJsonString(obj)
            parseObject(json, clazz)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 合并两个 JSON 对象
     * @param json1 第一个 JSON 字符串
     * @param json2 第二个 JSON 字符串（优先级更高）
     * @return 合并后的 JSON 字符串
     */
    fun mergeJson(json1: String, json2: String): String {
        return try {
            val obj1 = JSON.parseObject(json1)
            val obj2 = JSON.parseObject(json2)
            obj1.putAll(obj2)
            JSON.toJSONString(obj1)
        } catch (e: Exception) {
            e.printStackTrace()
            json1
        }
    }
    
    /**
     * 验证字符串是否为有效的 JSON
     * @param json 要验证的字符串
     * @return 是否为有效 JSON
     */
    fun isValidJson(json: String?): Boolean {
        if (json.isNullOrEmpty()) return false
        return try {
            JSON.parse(json)
            true
        } catch (e: Exception) {
            false
        }
    }
}

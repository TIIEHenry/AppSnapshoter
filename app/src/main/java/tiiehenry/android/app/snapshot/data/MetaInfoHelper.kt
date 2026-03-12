package tiiehenry.android.app.snapshot.data

import android.os.ParcelFileDescriptor
import com.alibaba.fastjson2.JSON
import tiiehenry.android.app.snapshot.utils.JsonUtils
import tiiehenry.android.snapshot.file.IFileSystem
import java.io.File
import java.io.FileReader

/**
 * MetaInfo 操作辅助类
 * 提供读取和写入 meta-info.json 文件的便捷方法
 */
object MetaInfoHelper {

    const val META_INFO_FILE_NAME = "meta-info.json"
    const val PERMISSIONS_FILE_NAME = "permissions.json"
    const val DATA_ITEM_FILE_EXTENSION = ".json"

    /**
     * 保存单个 MetaDataItem 到指定目录
     * @param dataItem MetaDataItem 对象
     * @param archiveDir 存档目录
     * @param prettyFormat 是否格式化输出
     * @return 生成的文件名
     */
    fun saveDataItem(
        dataItem: MetaDataItem,
        archiveDir: File,
        prettyFormat: Boolean = true
    ): String {
        // 使用 name 字段作为文件名，确保唯一性
        val fileName = "${dataItem.name}${DATA_ITEM_FILE_EXTENSION}"
        val dataItemFile = File(archiveDir, fileName)

        JsonUtils.writeToFile(dataItem, dataItemFile, prettyFormat)
        return fileName
    }

    /**
     * 保存单个 MetaDataItem 到指定目录
     * @param dataItem MetaDataItem 对象
     * @param archivePath 存档目录路径
     * @param prettyFormat 是否格式化输出
     * @return 生成的文件名
     */
    fun saveDataItem(
        dataItem: MetaDataItem,
        archivePath: String,
        prettyFormat: Boolean = true
    ): String {
        return saveDataItem(dataItem, File(archivePath), prettyFormat)
    }

    /**
     * 批量保存 MetaDataItem 列表并返回文件名列表
     * @param dataItems MetaDataItem 列表
     * @param archiveDir 存档目录
     * @param prettyFormat 是否格式化输出
     * @return 文件名列表
     */
    fun saveDataItems(
        dataItems: List<MetaDataItem>,
        archiveDir: File,
        prettyFormat: Boolean = true
    ): List<String> {
        return dataItems.map { saveDataItem(it, archiveDir, prettyFormat) }
    }

    /**
     * 批量保存 MetaDataItem 列表并返回文件名列表
     * @param dataItems MetaDataItem 列表
     * @param archivePath 存档目录路径
     * @param prettyFormat 是否格式化输出
     * @return 文件名列表
     */
    fun saveDataItems(
        dataItems: List<MetaDataItem>,
        archivePath: String,
        prettyFormat: Boolean = true
    ): List<String> {
        return saveDataItems(dataItems, File(archivePath), prettyFormat)
    }

    /**
     * 读取单个 MetaDataItem
     * @param fileName 数据项文件名
     * @param archiveDir 存档目录
     * @return MetaDataItem 对象
     */
    fun readDataItemSafe(
        fileName: String,
        archiveDir: File
    ): MetaDataItem? {
        val dataItemFile = File(archiveDir, fileName)
        if (!dataItemFile.exists()) {
            return null
        }
        val json = dataItemFile.readText()
        return JSON.parseObject(json, MetaDataItem::class.java)
    }

    /**
     * 读取单个 MetaDataItem
     * @param fileName 数据项文件名
     * @param archiveDir 存档目录
     * @return MetaDataItem 对象
     */
    fun readDataItem(
        fileName: String,
        archiveDir: File
    ): MetaDataItem {
        val dataItemFile = File(archiveDir, fileName)
        val json = dataItemFile.readText()
        return JSON.parseObject(json, MetaDataItem::class.java)
    }

    /**
     * 读取单个 MetaDataItem
     * @param fileName 数据项文件名
     * @param archivePath 存档目录路径
     * @return MetaDataItem 对象
     */
    fun readDataItem(
        fileName: String,
        archivePath: String
    ): MetaDataItem {
        return readDataItem(fileName, File(archivePath))
    }

    /**
     * 根据文件名列表读取多个 MetaDataItem
     * @param fileNames 数据项文件名列表
     * @param archiveDir 存档目录
     * @return MetaDataItem 列表
     */
    fun readDataItems(
        fileNames: List<String>,
        archiveDir: File
    ): List<MetaDataItem> {
        return fileNames.mapNotNull { readDataItemSafe(it, archiveDir) }
    }

    /**
     * 根据文件名列表读取多个 MetaDataItem
     * @param fileNames 数据项文件名列表
     * @param archivePath 存档目录路径
     * @return MetaDataItem 列表
     */
    fun readDataItems(
        fileNames: List<String>,
        archivePath: String
    ): List<MetaDataItem> {
        return readDataItems(fileNames, File(archivePath))
    }

    fun read(fs: IFileSystem, jsonFile: String): MetaInfo {
        fs.openFile(jsonFile, ParcelFileDescriptor.MODE_READ_ONLY).use {
            val jsonStr = FileReader(it.fileDescriptor).readText()
            return JSON.parseObject(jsonStr, MetaInfo::class.java)
        }
    }

    /**
     * 读取 permissions.json 文件
     * @param fs 文件系统接口
     * @param permissionsFile permissions.json 文件路径
     * @return 权限列表，文件不存在或读取失败时返回空列表
     */
    fun readPermissions(fs: IFileSystem, permissionsFile: String): List<MetaPermission> {
        return try {
            fs.openFile(permissionsFile, ParcelFileDescriptor.MODE_READ_ONLY).use {
                val jsonStr = FileReader(it.fileDescriptor).readText()
                JSON.parseArray(jsonStr, MetaPermission::class.java) ?: emptyList()
            }
        } catch (e: Exception) {
            // 文件不存在或解析失败时返回空列表
            emptyList()
        }
    }

    /**
     * 写入 permissions.json 文件
     * @param permissions 权限列表
     * @param archiveDir 存档目录
     * @param prettyFormat 是否格式化输出
     * @return 是否写入成功
     */
    fun writePermissions(
        permissions: List<MetaPermission>,
        archiveDir: File,
        prettyFormat: Boolean = true
    ): Boolean {
        val permissionsFile = File(archiveDir, PERMISSIONS_FILE_NAME)
        return JsonUtils.writeToFile(permissions, permissionsFile, prettyFormat)
    }

    /**
     * 写入 permissions.json 文件
     * @param permissions 权限列表
     * @param archivePath 存档目录路径
     * @param prettyFormat 是否格式化输出
     * @return 是否写入成功
     */
    fun writePermissions(
        permissions: List<MetaPermission>,
        archivePath: String,
        prettyFormat: Boolean = true
    ): Boolean {
        return writePermissions(permissions, File(archivePath), prettyFormat)
    }


    /**
     * 将 MetaInfo 写入存档目录
     * 注意：permissions 字段不会被序列化到 meta-info.json 中，需要单独调用 writePermissions 方法保存
     * @param metaInfo MetaInfo 对象
     * @param archiveDir 存档目录
     * @param prettyFormat 是否格式化输出
     * @return 是否写入成功
     */
    fun writeToArchive(
        metaInfo: MetaInfo,
        archiveDir: File,
        prettyFormat: Boolean = true
    ): Boolean {
        val metaFile = File(archiveDir, META_INFO_FILE_NAME)
        return JsonUtils.writeToFile(metaInfo, metaFile, prettyFormat)
    }

    /**
     * 将 MetaInfo 写入存档目录
     * 注意：permissions 字段不会被序列化到 meta-info.json 中，需要单独调用 writePermissions 方法保存
     * @param metaInfo MetaInfo 对象
     * @param archivePath 存档目录路径
     * @param prettyFormat 是否格式化输出
     * @return 是否写入成功
     */
    fun writeToArchive(
        metaInfo: MetaInfo,
        archivePath: String,
        prettyFormat: Boolean = true
    ): Boolean {
        return writeToArchive(metaInfo, File(archivePath), prettyFormat)
    }

    /**
     * 获取存档的总大小（所有数据项的大小之和）
     * @param metaInfo MetaInfo 对象
     * @param archiveDir 存档目录
     * @return 总大小（字节）
     */
    fun getTotalSize(metaInfo: MetaInfo, archiveDir: File): Long {
        val dataItems = readDataItems(metaInfo.dataItems, archiveDir)
        return dataItems.sumOf { it.targetSize }
    }

    /**
     * 获取存档的总大小（所有数据项的大小之和）
     * @param metaInfo MetaInfo 对象
     * @param archivePath 存档目录路径
     * @return 总大小（字节）
     */
    fun getTotalSize(metaInfo: MetaInfo, archivePath: String): Long {
        return getTotalSize(metaInfo, File(archivePath))
    }

    /**
     * 获取解压后存档的总大小（预估）
     * @param metaInfo MetaInfo 对象
     * @param archiveDir 存档目录
     * @return 总大小（字节）
     */
    fun getPredictTotalSize(metaInfo: MetaInfo, archiveDir: File): Long {
        val dataItems = readDataItems(metaInfo.dataItems, archiveDir)
        return dataItems.sumOf { it.originSize }
    }

    /**
     * 获取解压后存档的总大小（预估）
     * @param metaInfo MetaInfo 对象
     * @param archivePath 存档目录路径
     * @return 总大小（字节）
     */
    fun getPredictTotalSize(metaInfo: MetaInfo, archivePath: String): Long {
        return getPredictTotalSize(metaInfo, File(archivePath))
    }

    /**
     * 检查存档是否包含特定的数据项
     * @param metaInfo MetaInfo 对象
     * @param itemName 数据项名称
     * @param archiveDir 存档目录
     * @return 是否包含
     */
    fun hasDataItem(metaInfo: MetaInfo, itemName: String, archiveDir: File): Boolean {
        val dataItems = readDataItems(metaInfo.dataItems, archiveDir)
        return dataItems.any { it.name == itemName }
    }

    /**
     * 检查存档是否包含特定的数据项
     * @param metaInfo MetaInfo 对象
     * @param itemName 数据项名称
     * @param archivePath 存档目录路径
     * @return 是否包含
     */
    fun hasDataItem(metaInfo: MetaInfo, itemName: String, archivePath: String): Boolean {
        return hasDataItem(metaInfo, itemName, File(archivePath))
    }

    /**
     * 获取特定名称的数据项
     * @param metaInfo MetaInfo 对象
     * @param itemName 数据项名称
     * @param archiveDir 存档目录
     * @return DataItem 对象，未找到返回 null
     */
    fun getDataItem(metaInfo: MetaInfo, itemName: String, archiveDir: File): MetaDataItem? {
        val dataItems = readDataItems(metaInfo.dataItems, archiveDir)
        return dataItems.find { it.name == itemName }
    }

    /**
     * 获取特定名称的数据项
     * @param metaInfo MetaInfo 对象
     * @param itemName 数据项名称
     * @param archivePath 存档目录路径
     * @return DataItem 对象，未找到返回 null
     */
    fun getDataItem(metaInfo: MetaInfo, itemName: String, archivePath: String): MetaDataItem? {
        return getDataItem(metaInfo, itemName, File(archivePath))
    }
}
package tiiehenry.android.app.snapshotor.data

import android.os.ParcelFileDescriptor
import com.alibaba.fastjson2.JSON
import tiiehenry.android.app.snapshotor.utils.JsonUtils
import tiiehenry.android.snapshotor.file.IFileSystem
import java.io.File
import java.io.FileReader

/**
 * MetaInfo 操作辅助类
 * 提供读取和写入 meta-info.json 文件的便捷方法
 */
object MetaInfoHelper {

    const val META_INFO_FILE_NAME = "meta-info.json"

    fun read(fs: IFileSystem, jsonFile: String): MetaInfo {
        fs.openFile(jsonFile, ParcelFileDescriptor.MODE_READ_ONLY).use {
            val jsonStr = FileReader(it.fileDescriptor).readText()
            return JSON.parseObject(jsonStr, MetaInfo::class.java)
        }
    }


    /**
     * 将 MetaInfo 写入存档目录
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
     * 创建一个新的 MetaInfo 对象
     * @param packageInfo 包信息
     * @param userId 用户ID
     * @param dataItems 数据项列表
     * @param permissions 权限列表
     * @param compressCost 压缩耗时
     * @return MetaInfo 对象
     */
    fun create(
        packageInfo: MetaPackageInfo,
        userId: Int = 0,
        dataItems: List<MetaDataItem> = emptyList(),
        permissions: List<MetaPermission> = emptyList(),
        compressCost: Long = 0
    ): MetaInfo {
        return MetaInfo(
            packageInfo,
            userId,
            dataItems,
            permissions,
            TimeInfo(
                compressCost,
                System.currentTimeMillis()
            )
        )
    }

    /**
     * 获取存档的总大小（所有数据项的大小之和）
     * @param metaInfo MetaInfo 对象
     * @return 总大小（字节）
     */
    fun getTotalSize(metaInfo: MetaInfo): Long {
        return metaInfo.dataItems.sumOf { it.targetSize }
    }

    /**
     * 获取解压后存档的总大小（预估）
     * @param metaInfo MetaInfo 对象
     * @return 总大小（字节）
     */
    fun getPredictTotalSize(metaInfo: MetaInfo): Long {
        return metaInfo.dataItems.sumOf { it.originSize }
    }

    /**
     * 获取存档的总压缩耗时（所有数据项的压缩耗时之和）
     * @param metaInfo MetaInfo 对象
     * @return 总耗时（毫秒）
     */
    fun getTotalCompressCost(metaInfo: MetaInfo): Long {
        return metaInfo.dataItems.sumOf { it.compressCost } + metaInfo.time.compressCost
    }

    /**
     * 检查存档是否包含特定的数据项
     * @param metaInfo MetaInfo 对象
     * @param itemName 数据项名称
     * @return 是否包含
     */
    fun hasDataItem(metaInfo: MetaInfo, itemName: String): Boolean {
        return metaInfo.dataItems.any { it.name == itemName }
    }

    /**
     * 获取特定名称的数据项
     * @param metaInfo MetaInfo 对象
     * @param itemName 数据项名称
     * @return DataItem 对象，未找到返回 null
     */
    fun getDataItem(metaInfo: MetaInfo, itemName: String): MetaDataItem? {
        return metaInfo.dataItems.find { it.name == itemName }
    }
}

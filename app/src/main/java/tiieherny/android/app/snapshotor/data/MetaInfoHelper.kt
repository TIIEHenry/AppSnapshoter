package tiieherny.android.app.snapshotor.data

import tiieherny.android.app.snapshotor.utils.JsonUtils
import java.io.File

/**
 * MetaInfo 操作辅助类
 * 提供读取和写入 meta-info.json 文件的便捷方法
 */
object MetaInfoHelper {

    private const val META_INFO_FILE_NAME = "meta-info.json"

    /**
     * 从存档目录读取 MetaInfo
     * @param archiveDir 存档目录
     * @return MetaInfo 对象，失败返回 null
     */
    fun readFromArchive(archiveDir: File): MetaInfo? {
        val metaFile = File(archiveDir, META_INFO_FILE_NAME)
        return JsonUtils.parseFromFile(metaFile, MetaInfo::class.java)
    }

    /**
     * 从存档目录路径读取 MetaInfo
     * @param archivePath 存档目录路径
     * @return MetaInfo 对象，失败返回 null
     */
    fun readFromArchive(archivePath: String): MetaInfo? {
        return readFromArchive(File(archivePath))
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
            packageInfo = packageInfo,
            userId = userId,
            dataItems = dataItems,
            permissions = permissions,
            time = TimeInfo(
                compressCost = compressCost,
                makeTime = System.currentTimeMillis()
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

package tiieherny.android.app.snapshotor.data

import com.alibaba.fastjson2.annotation.JSONField

/**
 * 数据项
 */
data class MetaDataItem(
    @JSONField(name = "algorithm")
    val algorithm: String,

    @JSONField(name = "name")
    val name: String,

    @JSONField(name = "file")
    val file: String,

    /**
     * 自定义路径，恢复时如果是apk，用install代表安装
     */
    @JSONField(name = "path")
    val path: String,

    @JSONField(name = "origin_size")
    val originSize: Long,

    @JSONField(name = "target_size")
    val targetSize: Long,

    @JSONField(name = "md5")
    val md5: String,

    @JSONField(name = "compressCost")
    val compressCost: Long
)
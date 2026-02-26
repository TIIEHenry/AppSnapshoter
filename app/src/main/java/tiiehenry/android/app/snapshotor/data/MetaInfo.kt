package tiiehenry.android.app.snapshotor.data

import com.alibaba.fastjson2.annotation.JSONField

/**
 * 存档元信息数据类
 * 对应 meta-info.json 文件结构
 */
data class MetaInfo(
    @JSONField(name = "packageInfo")
    val packageInfo: MetaPackageInfo,

    @JSONField(name = "userId")
    val userId: Int,

    @JSONField(name = "dataItems")
    val dataItems: List<MetaDataItem>,

    @JSONField(name = "permissions")
    val permissions: List<MetaPermission>,

    @JSONField(name = "time")
    val time: TimeInfo
)


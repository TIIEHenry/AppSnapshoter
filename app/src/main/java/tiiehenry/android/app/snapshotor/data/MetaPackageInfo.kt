package tiiehenry.android.app.snapshotor.data

import com.alibaba.fastjson2.annotation.JSONField

/**
 * 包信息
 */
data class MetaPackageInfo(
    @JSONField(name = "label")
    val label: String,

    @JSONField(name = "packageName")
    val packageName: String,

    @JSONField(name = "versionCode")
    val versionCode: Long,

    @JSONField(name = "versionName")
    val versionName: String?,

    @JSONField(name = "firstInstallTime")
    val firstInstallTime: Long,

    @JSONField(name = "flags")
    val flags: Int,

    @JSONField(name = "lastUpdateTime")
    val lastUpdateTime: Long
)
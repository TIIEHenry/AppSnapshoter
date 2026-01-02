package tiieherny.android.app.snapshotor.data

import com.alibaba.fastjson2.annotation.JSONField

/**
 * 时间信息
 */
data class TimeInfo(
    @JSONField(name = "compressCost")
    val compressCost: Long,

    @JSONField(name = "makeTime")
    val makeTime: Long
)
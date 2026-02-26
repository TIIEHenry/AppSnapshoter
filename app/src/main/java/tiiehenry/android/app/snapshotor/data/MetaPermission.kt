package tiiehenry.android.app.snapshotor.data

import com.alibaba.fastjson2.annotation.JSONField
import tiiehenry.android.snapshotor.app.AppPermission

/**
 * 权限信息
 */
data class MetaPermission(
    @JSONField(name = "isGranted")
    val isGranted: Boolean,

    @JSONField(name = "mode")
    val mode: Int,

    @JSONField(name = "name")
    val name: String,

    @JSONField(name = "op")
    val op: Int
) {
    companion object {
        /**
         * 从 AppPermission 转换为 MetaPermission
         */
        fun fromAppPermission(appPermission: AppPermission): MetaPermission {
            return MetaPermission(
                isGranted = appPermission.isGranted,
                mode = appPermission.mode,
                name = appPermission.name,
                op = appPermission.op
            )
        }
    }
}
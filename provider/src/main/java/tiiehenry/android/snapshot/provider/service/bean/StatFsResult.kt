package tiiehenry.android.snapshot.provider.service.bean

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 文件系统统计信息包装类
 */
@Parcelize
class StatFsResult(
    var availableBytes: Long = 0,
    var totalBytes: Long = 0
) : Parcelable {
}
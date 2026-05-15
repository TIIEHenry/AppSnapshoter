package tiiehenry.android.snapshot.app

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 *应用信息数据类
 */
@Parcelize
data class AppInfo(
    val packageName: String,
    val userId: Int,
    val detail: AppDetail
) : Parcelable
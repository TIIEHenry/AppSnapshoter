package tiiehenry.android.snapshot.app

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 *应用详细信息数据类
 */
@Parcelize
data class AppDetail(
    val uid: Int = 0,
    val label: String = "",
    val versionName: String = "",
    val versionCode: Long = 0L,
    val flags: Int = 0,
    val firstInstallTime: Long = 0L,
    val lastUpdateTime: Long = 0L
) : Parcelable
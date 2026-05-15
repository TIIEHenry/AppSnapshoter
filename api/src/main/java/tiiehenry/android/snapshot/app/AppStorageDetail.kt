package tiiehenry.android.snapshot.app

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 *储数据类
 */
@Parcelize
data class AppStorageDetail(
    val apkBytes: Long = 0L,
    val internalDataBytes: Long = 0L,
    val externalDataBytes: Long = 0L,
    val additionalDataBytes: Long = 0L
) : Parcelable
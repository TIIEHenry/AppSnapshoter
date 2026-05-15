package tiiehenry.android.snapshot.app

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 *用存储信息数据类
 */
@Parcelize
data class AppStorage(
    val packageName: String,
    val userId: Int,
    val detail: AppStorageDetail
) : Parcelable
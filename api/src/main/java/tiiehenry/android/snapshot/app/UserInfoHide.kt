package tiiehenry.android.snapshot.app

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * UserInfo 的副本类，用于跨进程传递用户信息
 */
@Parcelize
class UserInfoHide(
    var id: Int,
    var name: String?
) : Parcelable

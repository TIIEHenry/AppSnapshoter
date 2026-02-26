package tiiehenry.android.snapshotor.file

import android.os.Parcel
import android.os.Parcelable

/**
 * 应用信息数据类
 */
data class AppInfo(
    val packageName: String,
    val userId: Int,
    val info: Info
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readParcelable(Info::class.java.classLoader) ?: Info()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(packageName)
        parcel.writeInt(userId)
        parcel.writeParcelable(info, flags)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<AppInfo> {
        override fun createFromParcel(parcel: Parcel): AppInfo {
            return AppInfo(parcel)
        }

        override fun newArray(size: Int): Array<AppInfo?> {
            return arrayOfNulls(size)
        }
    }
}
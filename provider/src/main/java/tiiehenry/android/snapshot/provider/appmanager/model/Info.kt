package tiiehenry.android.snapshot.provider.appmanager.model

import android.os.Parcel
import android.os.Parcelable

/**
 *应用详细信息数据类
 */
data class Info(
    val uid: Int = 0,
    val label: String = "",
    val versionName: String = "",
    val versionCode: Long = 0L,
    val flags: Int = 0,
    val firstInstallTime: Long = 0L,
    val lastUpdateTime: Long = 0L
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readLong(),
        parcel.readInt(),
        parcel.readLong(),
        parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(uid)
        parcel.writeString(label)
        parcel.writeString(versionName)
        parcel.writeLong(versionCode)
        parcel.writeInt(flags)
        parcel.writeLong(firstInstallTime)
        parcel.writeLong(lastUpdateTime)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Info> {
        override fun createFromParcel(parcel: Parcel): Info {
            return Info(parcel)
        }

        override fun newArray(size: Int): Array<Info?> {
            return arrayOfNulls(size)
        }
    }
}
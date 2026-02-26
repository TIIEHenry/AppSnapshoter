package tiiehenry.android.snapshotor.provider.appmanager.model

import android.os.Parcel
import android.os.Parcelable

/**
 *储数据类
 */
data class Storage(
    val apkBytes: Long = 0L,
    val internalDataBytes: Long = 0L,
    val externalDataBytes: Long = 0L,
    val additionalDataBytes: Long = 0L
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(apkBytes)
        parcel.writeLong(internalDataBytes)
        parcel.writeLong(externalDataBytes)
        parcel.writeLong(additionalDataBytes)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Storage> {
        override fun createFromParcel(parcel: Parcel): Storage {
            return Storage(parcel)
        }

        override fun newArray(size: Int): Array<Storage?> {
            return arrayOfNulls(size)
        }
    }
}
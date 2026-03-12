package tiiehenry.android.snapshot.provider.appmanager.parcelables

import android.os.Parcel
import android.os.Parcelable

/**
 * 文件系统统计信息包装类
 */
class StatFsParcelable() : Parcelable {
    var availableBytes: Long = 0
    var totalBytes: Long = 0

    constructor(availableBytes: Long, totalBytes: Long) : this() {
        this.availableBytes = availableBytes
        this.totalBytes = totalBytes
    }

    constructor(parcel: Parcel) : this() {
        availableBytes = parcel.readLong()
        totalBytes = parcel.readLong()
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(availableBytes)
        parcel.writeLong(totalBytes)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<StatFsParcelable> {
        override fun createFromParcel(parcel: Parcel): StatFsParcelable {
            return StatFsParcelable(parcel)
        }

        override fun newArray(size: Int): Array<StatFsParcelable?> {
            return arrayOfNulls(size)
        }
    }
}
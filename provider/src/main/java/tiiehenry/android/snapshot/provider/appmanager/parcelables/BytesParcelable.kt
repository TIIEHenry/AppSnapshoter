package tiiehenry.android.snapshot.provider.appmanager.parcelables

import android.os.Parcel
import android.os.Parcelable

/**
 * 字节数组包装类，用于跨进程传输
 */
class BytesParcelable() : Parcelable {
    var size: Int = 0
    var bytes: ByteArray = ByteArray(0)

    constructor(size: Int, bytes: ByteArray) : this() {
        this.size = size
        this.bytes = bytes
    }

    constructor(parcel: Parcel) : this() {
        size = parcel.readInt()
        bytes = parcel.createByteArray() ?: ByteArray(0)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(size)
        parcel.writeByteArray(bytes)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<BytesParcelable> {
        override fun createFromParcel(parcel: Parcel): BytesParcelable {
            return BytesParcelable(parcel)
        }

        override fun newArray(size: Int): Array<BytesParcelable?> {
            return arrayOfNulls(size)
        }
    }
}
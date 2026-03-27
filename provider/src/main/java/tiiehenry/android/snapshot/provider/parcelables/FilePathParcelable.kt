package tiiehenry.android.snapshot.provider.parcelables

import android.os.Parcel
import android.os.Parcelable

/**
 * 文件路径信息包装类
 */
class FilePathParcelable() : Parcelable {
    var path: String = ""
    var type: Int = -1 // 0: file, 1: directory, -1: unknown

    constructor(path: String, type: Int) : this() {
        this.path = path
        this.type = type
    }

    constructor(parcel: Parcel) : this() {
        path = parcel.readString() ?: ""
        type = parcel.readInt()
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(path)
        parcel.writeInt(type)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<FilePathParcelable> {
        override fun createFromParcel(parcel: Parcel): FilePathParcelable {
            return FilePathParcelable(parcel)
        }

        override fun newArray(size: Int): Array<FilePathParcelable?> {
            return arrayOfNulls(size)
        }
    }
}
package tiiehenry.android.snapshotor.provider.appmanager.model

import android.os.Parcel
import android.os.Parcelable

/**
 *用存储信息数据类
 */
data class AppStorage(
    val packageName: String,
    val userId: Int,
    val storage: Storage
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readParcelable(Storage::class.java.classLoader) ?: Storage()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(packageName)
        parcel.writeInt(userId)
        parcel.writeParcelable(storage, flags)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<AppStorage> {
        override fun createFromParcel(parcel: Parcel): AppStorage {
            return AppStorage(parcel)
        }

        override fun newArray(size: Int): Array<AppStorage?> {
            return arrayOfNulls(size)
        }
    }
}
package tiiehenry.android.snapshotor.util

import android.os.Parcel
import android.os.Parcelable

object ParcelableHelper {

    fun <T : Parcelable> Parcel.readParcelable(loader: ClassLoader?): T? {
        return this.readParcelable(loader)
    }

    fun <T : Parcelable> Parcel.writeParcelable(parcelable: T?, flags: Int) {
        this.writeParcelable(parcelable, flags)
    }

    fun <T : Parcelable> Parcel.readTypedList(list: MutableList<T>, creator: Parcelable.Creator<T>) {
        this.readTypedList(list, creator)
    }

    fun <T : Parcelable> Parcel.writeTypedList(list: List<T>) {
        this.writeTypedList(list)
    }

    fun <T : Parcelable> Parcel.readTypedArray(creator: Parcelable.Creator<T>): Array<T?> {
        return this.readTypedArray(creator)
    }

    fun <T : Parcelable> Parcel.writeTypedArray(array: Array<out T?>, flags: Int) {
        this.writeTypedArray(array, flags)
    }

    fun Parcel.readString(): String? {
        return this.readString()
    }

    fun Parcel.writeString(str: String?) {
        this.writeString(str)
    }

    fun Parcel.writeInt(value: Int) {
        this.writeInt(value)
    }

    fun Parcel.readInt(): Int {
        return this.readInt()
    }

    fun Parcel.writeLong(value: Long) {
        this.writeLong(value)
    }

    fun Parcel.readLong(): Long {
        return this.readLong()
    }

    fun Parcel.writeFloat(value: Float) {
        this.writeFloat(value)
    }

    fun Parcel.readFloat(): Float {
        return this.readFloat()
    }

    fun Parcel.writeDouble(value: Double) {
        this.writeDouble(value)
    }

    fun Parcel.readDouble(): Double {
        return this.readDouble()
    }

    fun Parcel.writeBoolean(value: Boolean) {
        this.writeInt(if (value) 1 else 0)
    }

    fun Parcel.readBoolean(): Boolean {
        return this.readInt() != 0
    }

    fun <T : Parcelable> T.marshall(): ByteArray {
        val parcel = Parcel.obtain()
        parcel.writeParcelable(this, 0)
        val bytes = parcel.marshall()
        parcel.recycle()
        return bytes
    }

    fun <T : Parcelable> ByteArray.unmarshall(creator: Parcelable.Creator<T>): T {
        val parcel = Parcel.obtain()
        parcel.unmarshall(this, 0, this.size)
        parcel.setDataPosition(0)
        val obj = creator.createFromParcel(parcel)
        parcel.recycle()
        return obj
    }

    fun ByteArray.unmarshall(block: (Parcel) -> Unit) {
        val parcel = Parcel.obtain()
        parcel.unmarshall(this, 0, this.size)
        parcel.setDataPosition(0)
        block(parcel)
        parcel.recycle()
    }
}
package tiiehenry.android.snapshot.provider.appmanager.util

import android.os.Parcel

/**
 * Parcelable工具类，提供序列化和反序列化功能
 */
object ParcelableHelper {
    
    /**
     *将Parcel对象序列化为字节数组
     */
    fun Parcel.marshall(): ByteArray {
        return this.marshall()
    }
    
    /**
     * 从字节数组反序列化为Parcel对象
     */
    fun ByteArray.unmarshall(block: (Parcel) -> Unit) {
        val parcel = Parcel.obtain()
        try {
            parcel.unmarshall(this, 0, this.size)
            parcel.setDataPosition(0)
            block(parcel)
        } finally {
            parcel.recycle()
        }
    }
    
    /**
     *扩函数：将Parcelable对象转换为字节数组
     */
    fun <T : android.os.Parcelable> T.toBytes(): ByteArray {
        val parcel = Parcel.obtain()
        return try {
            writeToParcel(parcel, 0)
            parcel.marshall()
        } finally {
            parcel.recycle()
        }
    }
    
    /**
     *扩函数：从字节数组创建Parcelable对象
     */
    inline fun <reified T : android.os.Parcelable> ByteArray.toParcelable(creator: android.os.Parcelable.Creator<T>): T? {
        val parcel = Parcel.obtain()
        return try {
            parcel.unmarshall(this, 0, this.size)
            parcel.setDataPosition(0)
            creator.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
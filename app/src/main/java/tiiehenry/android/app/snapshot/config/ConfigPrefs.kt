package tiiehenry.android.app.snapshot.config

import com.tencent.mmkv.MMKV

/** MMKV 里本组 / 本组集实际用到的读写面。单测用内存实现，避免初始化 MMKV。 */
internal interface ConfigPrefs {
    fun decodeString(key: String): String?
    fun encode(key: String, value: String)
    fun containsKey(key: String): Boolean
    fun decodeBool(key: String, defaultValue: Boolean): Boolean
    fun encode(key: String, value: Boolean)
    fun decodeInt(key: String, defaultValue: Int): Int
    fun encode(key: String, value: Int)
    fun removeValueForKey(key: String)
    fun clearAll()
}

internal class MmkvConfigPrefs(val mmkv: MMKV) : ConfigPrefs {
    override fun decodeString(key: String): String? = mmkv.decodeString(key)

    override fun encode(key: String, value: String) {
        mmkv.encode(key, value)
    }

    override fun containsKey(key: String): Boolean = mmkv.containsKey(key)

    override fun decodeBool(key: String, defaultValue: Boolean): Boolean =
        mmkv.decodeBool(key, defaultValue)

    override fun encode(key: String, value: Boolean) {
        mmkv.encode(key, value)
    }

    override fun decodeInt(key: String, defaultValue: Int): Int = mmkv.decodeInt(key, defaultValue)

    override fun encode(key: String, value: Int) {
        mmkv.encode(key, value)
    }

    override fun removeValueForKey(key: String) {
        mmkv.removeValueForKey(key)
    }

    override fun clearAll() {
        mmkv.clearAll()
    }
}

package tiieherny.android.app.snapshotor.config

import com.tencent.mmkv.MMKV

class SortConfig(private val mmkv: MMKV) {
    private companion object {
        const val KEY_SORT_TYPE = "sortType"
        const val KEY_SORT_REVERSE = "sortReverse"
        const val KEY_SORT_ORDER = "sortOrder"
    }

    var sortType: Int
        get() = mmkv.decodeInt(KEY_SORT_TYPE, 0)
        set(value) {
            mmkv.encode(KEY_SORT_TYPE, value)
        }

    var sortReverse: Boolean
        get() = mmkv.decodeBool(KEY_SORT_REVERSE, false)
        set(value) {
            mmkv.encode(KEY_SORT_REVERSE, value)
        }

    var sortOrder: Set<String>
        get() = mmkv.decodeStringSet(KEY_SORT_ORDER, emptySet()) ?: emptySet()
        set(value) {
            mmkv.encode(KEY_SORT_ORDER, value)
        }
}
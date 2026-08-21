package tiiehenry.android.app.snapshot.utils

import com.tencent.mmkv.MMKV

/**
 * 持久化记录由快照流程挂起、尚未成功解冻的应用，用于进程异常退出后的兜底恢复。
 */
object AppSuspendTracker {

    private const val MMKV_KEY = "pending_unsuspend_packages"

    private fun mmkv(): MMKV = MMKV.defaultMMKV()

    private fun entryKey(packageName: String, userId: Int): String = "$packageName@$userId"

    fun parseEntry(entry: String): Pair<String, Int>? {
        val separator = entry.lastIndexOf('@')
        if (separator <= 0 || separator >= entry.length - 1) return null
        val packageName = entry.substring(0, separator)
        val userId = entry.substring(separator + 1).toIntOrNull() ?: return null
        return packageName to userId
    }

    fun isTracked(packageName: String, userId: Int): Boolean {
        return mmkv().decodeStringSet(MMKV_KEY, emptySet())?.contains(entryKey(packageName, userId)) == true
    }

    fun markSuspended(packageName: String, userId: Int) {
        val current = mmkv().decodeStringSet(MMKV_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(entryKey(packageName, userId))
        mmkv().encode(MMKV_KEY, current)
    }

    fun markReleased(packageName: String, userId: Int) {
        val current = mmkv().decodeStringSet(MMKV_KEY, emptySet())?.toMutableSet() ?: return
        if (!current.remove(entryKey(packageName, userId))) return
        mmkv().encode(MMKV_KEY, current)
    }

    fun pendingEntries(): List<Pair<String, Int>> {
        return mmkv().decodeStringSet(MMKV_KEY, emptySet())
            ?.mapNotNull { parseEntry(it) }
            ?: emptyList()
    }
}

package tiiehenry.android.app.snapshot.main.launch.batch

import com.alibaba.fastjson2.JSON
import tiiehenry.android.app.snapshot.group.SnapGroup

object RestoreRecordStore {

    private fun mmkvKey(packageName: String, userId: Int): String =
        "restore_record:$packageName:$userId"

    internal fun parseRecord(json: String): RestoreRecord? {
        return try {
            val obj = JSON.parseObject(json) ?: return null
            val archiveName = obj.getString("archiveName")
            if (archiveName.isNullOrBlank()) return null
            RestoreRecord(
                restoredAt = obj.getLongValue("restoredAt"),
                archiveName = archiveName,
                archiveMakeTime = obj.getLongValue("archiveMakeTime")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun get(group: SnapGroup, packageName: String, userId: Int): RestoreRecord? {
        val json = group.mmkv.decodeString(mmkvKey(packageName, userId)) ?: return null
        return parseRecord(json)
    }

    fun put(group: SnapGroup, packageName: String, userId: Int, record: RestoreRecord) {
        if (record.archiveName.isBlank()) return
        group.mmkv.encode(mmkvKey(packageName, userId), JSON.toJSONString(record))
    }

    fun remove(group: SnapGroup, packageName: String, userId: Int) {
        group.mmkv.removeValueForKey(mmkvKey(packageName, userId))
    }

    /** key = `packageName:userId` */
    fun loadAll(group: SnapGroup): Map<String, RestoreRecord> {
        val prefix = "restore_record:"
        val result = mutableMapOf<String, RestoreRecord>()
        for (key in group.mmkv.allKeys().orEmpty()) {
            if (!key.startsWith(prefix)) continue
            val json = group.mmkv.decodeString(key) ?: continue
            val record = parseRecord(json) ?: continue
            val suffix = key.removePrefix(prefix)
            result[suffix] = record
        }
        return result
    }
}

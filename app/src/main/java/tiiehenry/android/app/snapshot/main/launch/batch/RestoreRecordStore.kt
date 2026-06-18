package tiiehenry.android.app.snapshot.main.launch.batch

import com.alibaba.fastjson2.JSON
import tiiehenry.android.app.snapshot.group.SnapGroup

object RestoreRecordStore {

    private fun mmkvKey(packageName: String, userId: Int): String =
        "restore_record:$packageName:$userId"

    fun get(group: SnapGroup, packageName: String, userId: Int): RestoreRecord? {
        val json = group.mmkv.decodeString(mmkvKey(packageName, userId)) ?: return null
        return JSON.parseObject(json, RestoreRecord::class.java)
    }

    fun put(group: SnapGroup, packageName: String, userId: Int, record: RestoreRecord) {
        group.mmkv.encode(mmkvKey(packageName, userId), JSON.toJSONString(record))
    }

    /** key = `packageName:userId` */
    fun loadAll(group: SnapGroup): Map<String, RestoreRecord> {
        val prefix = "restore_record:"
        val result = mutableMapOf<String, RestoreRecord>()
        for (key in group.mmkv.allKeys().orEmpty()) {
            if (!key.startsWith(prefix)) continue
            val json = group.mmkv.decodeString(key) ?: continue
            val record = JSON.parseObject(json, RestoreRecord::class.java) ?: continue
            val suffix = key.removePrefix(prefix)
            result[suffix] = record
        }
        return result
    }
}

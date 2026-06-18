package tiiehenry.android.app.snapshot.main.launch.batch

import com.alibaba.fastjson2.JSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestoreRecordStoreTest {

    @Test
    fun parseRecord_validJson() {
        val json = """{"restoredAt":100,"archiveName":"snap.tar.zst","archiveMakeTime":200}"""
        assertEquals(RestoreRecord(100L, "snap.tar.zst", 200L), RestoreRecordStore.parseRecord(json))
    }

    @Test
    fun parseRecord_roundTripFromSerialization() {
        val record = RestoreRecord(100L, "snap.tar.zst", 200L)
        val json = JSON.toJSONString(record)
        assertEquals(record, RestoreRecordStore.parseRecord(json))
    }

    @Test
    fun parseRecord_nullArchiveName() {
        val json = """{"restoredAt":100,"archiveName":null,"archiveMakeTime":200}"""
        assertNull(RestoreRecordStore.parseRecord(json))
    }

    @Test
    fun parseRecord_missingArchiveName() {
        val json = """{"restoredAt":100,"archiveMakeTime":200}"""
        assertNull(RestoreRecordStore.parseRecord(json))
    }

    @Test
    fun parseRecord_blankArchiveName() {
        val json = """{"restoredAt":100,"archiveName":"","archiveMakeTime":200}"""
        assertNull(RestoreRecordStore.parseRecord(json))
    }

    @Test
    fun parseRecord_malformedJson() {
        assertNull(RestoreRecordStore.parseRecord("{invalid"))
    }
}

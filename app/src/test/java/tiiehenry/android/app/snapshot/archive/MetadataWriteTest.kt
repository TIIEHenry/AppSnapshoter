package tiiehenry.android.app.snapshot.archive

import org.junit.Assert.*
import org.junit.Test
import com.alibaba.fastjson2.JSON
import tiiehenry.android.app.snapshot.archive.bean.MetaDataItem
import tiiehenry.android.app.snapshot.archive.bean.MetaInfo
import java.io.File
import java.io.IOException
import java.nio.file.Files

class MetadataWriteTest {
    @Test fun `data descriptor write failure propagates instead of returning a filename`() {
        val root = Files.createTempDirectory("metadata-write-test").toFile()
        try {
            val blocked = File(root, "not-a-directory").apply { writeText("occupied") }
            assertThrows(IOException::class.java) {
                MetaInfoHelper.saveDataItem(MetaDataItem().apply { name = "user" }, blocked)
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun `pending manifest write failure propagates`() {
        val root = Files.createTempDirectory("manifest-write-test").toFile()
        try {
            val blocked = File(root, "not-a-directory").apply { writeText("occupied") }
            assertThrows(IOException::class.java) { MetaInfoHelper.writePendingMetaInfo(MetaInfo(), blocked) }
        } finally { root.deleteRecursively() }
    }
    @Test fun `file source marker survives JSON and old descriptors default to directories`() {
        val source = MetaDataItem().apply { isSourceFile = true }
        assertTrue(JSON.parseObject(JSON.toJSONString(source), MetaDataItem::class.java).isSourceFile)
        assertFalse(JSON.parseObject("{}", MetaDataItem::class.java).isSourceFile)
    }

}

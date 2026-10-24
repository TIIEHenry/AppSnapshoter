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

    @Test fun `descriptor name that leaves the archive directory is not written`() {
        val root = Files.createTempDirectory("metadata-escape").toFile()
        try {
            val outside = root.parentFile
            val before = outside.list()?.toSet()
            assertThrows(IOException::class.java) {
                MetaInfoHelper.saveDataItem(MetaDataItem().apply { name = "../evil" }, root)
            }
            assertEquals(before, outside.list()?.toSet())
            assertFalse(File(outside, "evil.json").exists())
            assertTrue(root.list().isNullOrEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test fun `nested descriptor name inside the archive directory is written`() {
        val root = Files.createTempDirectory("metadata-nested").toFile()
        try {
            val fileName = MetaInfoHelper.saveDataItem(MetaDataItem().apply { name = "foo/bar" }, root)
            val written = File(root, fileName)
            assertTrue(written.isFile)
            assertTrue(written.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()))
        } finally { root.deleteRecursively() }
    }

    @Test fun `built-in descriptor name user is still written`() {
        val root = Files.createTempDirectory("metadata-user").toFile()
        try {
            val fileName = MetaInfoHelper.saveDataItem(MetaDataItem().apply { name = "user" }, root)
            assertEquals("user.json", fileName)
            assertTrue(File(root, fileName).isFile)
            assertTrue(File(root, fileName).canonicalFile.toPath().startsWith(root.canonicalFile.toPath()))
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

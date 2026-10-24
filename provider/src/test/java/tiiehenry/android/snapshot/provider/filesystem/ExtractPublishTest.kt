package tiiehenry.android.snapshot.provider.filesystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractPublishTest {
    @Test fun `scratch directories sit beside the target`() {
        val scratch = extractScratch("/data/user/0/pkg", "1")
        assertEquals("/data/user/0/.pkg.extract-1", scratch.temp)
        assertEquals("/data/user/0/.pkg.backup-1", scratch.backup)
        assertFalse(scratch.temp.startsWith("/data/user/0/pkg/"))
        assertFalse(scratch.backup.startsWith("/data/user/0/pkg/"))
    }

    @Test fun `a failed or cancelled extract leaves the preset file`() {
        val files = mutableMapOf("target/preset" to "old", "target/sibling" to "keep")
        files["temp/partial"] = "new"
        if (shouldCommitExtract(cancelled = true, extracted = false)) {
            publish(files)
        } else {
            discard(files, "temp")
        }
        assertEquals("old", files["target/preset"])
        assertEquals("keep", files["target/sibling"])
        assertFalse(files.keys.any { it.startsWith("temp") })
        assertFalse(shouldCommitExtract(cancelled = false, extracted = false))
    }

    @Test fun `a successful extract replaces the preset file`() {
        val files = mutableMapOf(
            "target/preset" to "old",
            "neighbor/keep" to "keep",
            "temp/from-archive" to "new"
        )
        assertTrue(shouldCommitExtract(cancelled = false, extracted = true))
        assertTrue(publish(files))
        assertEquals("new", files["target/from-archive"])
        assertEquals("keep", files["neighbor/keep"])
        assertFalse(files.containsKey("target/preset"))
        assertFalse(files.keys.any { it.startsWith("temp") || it.startsWith("backup") })
    }

    @Test fun `a failed publish restores the preset file`() {
        val files = mutableMapOf(
            "target/preset" to "old",
            "temp/from-archive" to "new"
        )
        assertFalse(publish(files, failTempMove = true))
        assertEquals("old", files["target/preset"])
        assertFalse(files.keys.any { it.startsWith("backup") || it.startsWith("temp") })
    }

    private fun publish(files: MutableMap<String, String>, failTempMove: Boolean = false): Boolean =
        publishExtractedDirectory(
            "temp",
            "target",
            "backup",
            exists = { path -> files.keys.any { it == path || it.startsWith("$path/") } },
            move = { from, to ->
                val blocked = failTempMove && from == "temp"
                val keys = files.keys.filter { it == from || it.startsWith("$from/") }
                val occupied = files.keys.any { it == to || it.startsWith("$to/") }
                if (!blocked && keys.isNotEmpty() && !occupied) {
                    keys.forEach { key ->
                        files[to + key.removePrefix(from)] = files.remove(key)!!
                    }
                    true
                } else {
                    false
                }
            },
            delete = { path ->
                val keys = files.keys.filter { it == path || it.startsWith("$path/") }
                keys.forEach { files.remove(it) }
                keys.isNotEmpty()
            }
        )

    private fun discard(files: MutableMap<String, String>, path: String) {
        files.keys.filter { it == path || it.startsWith("$path/") }.toList().forEach { files.remove(it) }
    }
}

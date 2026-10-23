package tiiehenry.android.app.snapshot.archive.make

import org.junit.Assert.*
import org.junit.Test

class SnapshotCommitTest {
    @Test fun `metadata failure cannot publish or uninstall`() {
        val events = mutableListOf<String>()
        assertThrows(IllegalStateException::class.java) {
            commitSnapshot({ error("disk full") }, { events += "validate" },
                { events += "publish" }, { events += "uninstall" })
        }
        assertTrue(events.isEmpty())
    }

    @Test fun `invalid completed payload cannot publish or uninstall`() {
        val events = mutableListOf<String>()
        assertThrows(IllegalStateException::class.java) {
            commitSnapshot({ events += "metadata" }, { error("checksum mismatch") },
                { events += "publish" }, { events += "uninstall" })
        }
        assertEquals(listOf("metadata"), events)
    }

    @Test fun `failed publication cannot uninstall`() {
        var uninstalled = false
        assertThrows(IllegalStateException::class.java) {
            commitSnapshot({}, {}, { error("rename failed") }, { uninstalled = true })
        }
        assertFalse(uninstalled)
    }

    @Test fun `uninstall follows verified publication`() {
        val events = mutableListOf<String>()
        commitSnapshot({ events += "metadata" }, { events += "validate" },
            { events += "publish" }, { events += "uninstall" })
        assertEquals(listOf("metadata", "validate", "publish", "uninstall"), events)
    }
}

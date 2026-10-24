package tiiehenry.android.app.snapshot.archive.make

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import tiiehenry.android.app.snapshot.archive.archiveChildStaysInside
import tiiehenry.android.app.snapshot.archive.extraItemNamesConflict
import tiiehenry.android.app.snapshot.archive.prepareExtraItemCompression
import java.io.IOException

class ExtraItemSnapshotGateTest {
    @Test fun `two enabled extra items with the same name conflict`() {
        assertTrue(extraItemNamesConflict(listOf("notes", "notes")))
        assertFalse(extraItemNamesConflict(listOf("notes", "documents")))
    }

    @Test fun `extra name matching a built-in task key conflicts`() {
        listOf("apk", "data", "user", "user_de", "obb", "media", "meta-info", "uninstall-app").forEach { name ->
            assertTrue(name, extraItemNamesConflict(listOf(name)))
        }
    }

    @Test fun `conflicting extra names do not compress`() {
        listOf(listOf("notes", "notes"), listOf("data"), listOf("meta-info"), listOf("uninstall-app")).forEach { names ->
            var compressed = false
            assertThrows(IOException::class.java) {
                prepareExtraItemCompression("/archive", names) { compressed = true }
            }
            assertFalse(compressed)
        }
    }

    @Test fun `extra names that normalize outside the archive do not compress`() {
        listOf("../evil", "foo/../../x", "../", "/etc/passwd").forEach { name ->
            var compressed = false
            assertThrows(IOException::class.java) {
                prepareExtraItemCompression("/archive", listOf("notes", name)) { compressed = true }
            }
            assertFalse(compressed)
            assertFalse(archiveChildStaysInside("/archive", name, ".tar.zst"))
        }
    }

    @Test fun `nested extra names that stay inside the archive may compress`() {
        val compressed = mutableListOf<String>()
        prepareExtraItemCompression("/archive", listOf("foo/bar", "..", "foo..bar")) { compressed += it }
        assertEquals(listOf("foo/bar", "..", "foo..bar"), compressed)
        assertTrue(archiveChildStaysInside("/archive", "foo/bar", ".tar.zst"))
        assertTrue(archiveChildStaysInside("/archive", "..", ".tar.zst"))
        assertTrue(archiveChildStaysInside("/archive", "foo..bar", ".json"))
    }
}

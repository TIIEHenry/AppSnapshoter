package tiiehenry.android.app.snapshot.archive.manage

import org.junit.Assert.*
import org.junit.Test

class ArchiveDeletionTest {
    @Test fun `failed deletion preserves referenced APK`() {
        var cleaned = false
        assertFalse(deleteArchiveThenCleanup({ false }, { cleaned = true }))
        assertFalse(cleaned)
    }

    @Test fun `throwing filesystem preserves referenced APK`() {
        var cleaned = false
        assertFalse(deleteArchiveThenCleanup({ error("read-only") }, { cleaned = true }))
        assertFalse(cleaned)
    }

    @Test fun `successful deletion permits APK cleanup`() {
        var cleaned = false
        assertTrue(deleteArchiveThenCleanup({ true }, { cleaned = true }))
        assertTrue(cleaned)
    }
}

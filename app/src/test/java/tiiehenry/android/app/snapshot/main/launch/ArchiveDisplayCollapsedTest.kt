package tiiehenry.android.app.snapshot.main.launch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveDisplayCollapsedTest {

    @Test
    fun `blank query uses live collapse`() {
        assertTrue(archiveDisplayCollapsed("", true))
        assertFalse(archiveDisplayCollapsed("", false))
        assertTrue(archiveDisplayCollapsed("   ", true))
    }

    @Test
    fun `nonblank query is never collapsed`() {
        assertFalse(archiveDisplayCollapsed("we", true))
        assertFalse(archiveDisplayCollapsed("we", false))
    }
}

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

    @Test
    fun `same group blank query keeps sort mode`() {
        assertFalse(shouldExitSortModeOnBind("", groupChanged = false, isSortMode = true))
        assertFalse(shouldExitSortModeOnBind("   ", groupChanged = false, isSortMode = true))
    }

    @Test
    fun `nonblank query exits sort mode even when group id unchanged`() {
        assertTrue(shouldExitSortModeOnBind("wechat", groupChanged = false, isSortMode = true))
    }

    @Test
    fun `group change exits sort mode`() {
        assertTrue(shouldExitSortModeOnBind("", groupChanged = true, isSortMode = true))
    }

    @Test
    fun `inactive sort mode is not exited`() {
        assertFalse(shouldExitSortModeOnBind("wechat", groupChanged = true, isSortMode = false))
    }
}

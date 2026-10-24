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

    @Test fun `clear-all is false when any deletion is false`() {
        assertFalse(clearedArchivesSucceeded(listOf(true, false)))
        assertFalse(clearedArchivesSucceeded(listOf(false)))
    }

    @Test fun `clear-all is true when every deletion is true or nothing is deleted`() {
        assertTrue(clearedArchivesSucceeded(listOf(true, true)))
        assertTrue(clearedArchivesSucceeded(emptyList()))
    }

    @Test fun `complete deletion is false when the package directory deletion is false`() {
        assertFalse(completeAppDeletionSucceeded(packageDirDeleted = false, iconInsidePackageDir = false, iconDeleted = true))
        assertFalse(completeAppDeletionSucceeded(packageDirDeleted = false, iconInsidePackageDir = true, iconDeleted = false))
    }

    @Test fun `icon outside the package directory succeeds only when both deletions succeed`() {
        assertFalse(iconResidesInPackageDir("/data/pkg", "/data/other/icon.png"))
        assertTrue(completeAppDeletionSucceeded(packageDirDeleted = true, iconInsidePackageDir = false, iconDeleted = true))
        assertFalse(completeAppDeletionSucceeded(packageDirDeleted = true, iconInsidePackageDir = false, iconDeleted = false))
    }

    @Test fun `icon inside the package directory ignores a later false icon deletion`() {
        assertTrue(iconResidesInPackageDir("/data/pkg", "/data/pkg/icon.png"))
        assertTrue(completeAppDeletionSucceeded(packageDirDeleted = true, iconInsidePackageDir = true, iconDeleted = false))
    }
}

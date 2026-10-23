package tiiehenry.android.app.snapshot.archive.make

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotWriteTargetTest {

    @Test
    fun `write dir is the card packageDir`() {
        assertEquals(
            "/storage/emulated/0/AsyncStudio/SnapShared/Vias/mark.via",
            snapshotWritePackageDir("/storage/emulated/0/AsyncStudio/SnapShared/Vias/mark.via"),
        )
    }

    @Test
    fun `reconstructed root plus package is a mismatch against another group`() {
        assertTrue(
            snapshotWritePathMismatch(
                cardPackageDir = "/storage/emulated/0/AsyncStudio/SnapShared/Vias/mark.via",
                groupRootPath = "/storage/emulated/0/AsyncStudio/SnapShared",
                packageName = "mark.via",
            )
        )
    }

    @Test
    fun `same group root and card dir is not a mismatch`() {
        assertFalse(
            snapshotWritePathMismatch(
                cardPackageDir = "/storage/emulated/0/AsyncStudio/SnapShared/Vias/mark.via",
                groupRootPath = "/storage/emulated/0/AsyncStudio/SnapShared/Vias",
                packageName = "mark.via",
            )
        )
    }
}

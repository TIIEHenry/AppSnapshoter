package tiiehenry.android.app.snapshot.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import tiiehenry.android.app.snapshot.group.ArchiveRoot

class GroupSetDeletionTest {

    @Test
    fun `delete files false keeps registration and reports failure`() {
        val groups = mutableListOf("m1", "other")
        val roots = mutableListOf<ArchiveRoot>(ArchiveRoot.Set("s"), ArchiveRoot.Group("other"))

        val plan = planGroupSetDeletion(
            mode = AppDataRepository.DeleteGroupSetMode.DELETE_FILES,
            setId = "s",
            memberIds = listOf("m1"),
            groups = groups,
            archiveRoots = roots,
            deleteSetDirectory = { false },
        )

        assertFalse(plan.success)
        assertSame(groups, plan.groups)
        assertSame(roots, plan.archiveRoots)
        assertTrue(plan.clearMemberIds.isEmpty())
        assertFalse(plan.clearSetLocal)
        assertEquals(listOf("m1", "other"), groups)
        assertEquals(listOf(ArchiveRoot.Set("s"), ArchiveRoot.Group("other")), roots)
    }

    @Test
    fun `delete files throw keeps registration and reports failure`() {
        val groups = mutableListOf("m1", "other")
        val roots = mutableListOf<ArchiveRoot>(ArchiveRoot.Set("s"), ArchiveRoot.Group("other"))

        val callbackSuccess = runCatching {
            planGroupSetDeletion(
                mode = AppDataRepository.DeleteGroupSetMode.DELETE_FILES,
                setId = "s",
                memberIds = listOf("m1"),
                groups = groups,
                archiveRoots = roots,
                deleteSetDirectory = { error("disk") },
            )
        }.fold(onSuccess = { it.success }, onFailure = { false })

        assertFalse(callbackSuccess)
        assertEquals(listOf("m1", "other"), groups)
        assertEquals(listOf(ArchiveRoot.Set("s"), ArchiveRoot.Group("other")), roots)
    }

    @Test
    fun `delete files true unregisters members and reports success`() {
        val groups = mutableListOf("m1", "other")
        val roots = mutableListOf<ArchiveRoot>(ArchiveRoot.Set("s"), ArchiveRoot.Group("other"))
        var deleted = false

        val plan = planGroupSetDeletion(
            mode = AppDataRepository.DeleteGroupSetMode.DELETE_FILES,
            setId = "s",
            memberIds = listOf("m1"),
            groups = groups,
            archiveRoots = roots,
            deleteSetDirectory = {
                deleted = true
                true
            },
        )

        assertTrue(deleted)
        assertTrue(plan.success)
        assertTrue(plan.clearSetLocal)
        assertEquals(listOf("other"), plan.groups)
        assertEquals(listOf("m1"), plan.clearMemberIds)
        assertEquals(listOf(ArchiveRoot.Group("other")), plan.archiveRoots)
        assertEquals(listOf("m1", "other"), groups)
        assertEquals(listOf(ArchiveRoot.Set("s"), ArchiveRoot.Group("other")), roots)
    }

    @Test
    fun `set and groups does not delete and still unregisters members`() {
        var deleted = false

        val plan = planGroupSetDeletion(
            mode = AppDataRepository.DeleteGroupSetMode.SET_AND_GROUPS,
            setId = "s",
            memberIds = listOf("m1"),
            groups = listOf("m1", "other"),
            archiveRoots = listOf(ArchiveRoot.Set("s"), ArchiveRoot.Group("other")),
            deleteSetDirectory = {
                deleted = true
                true
            },
        )

        assertFalse(deleted)
        assertTrue(plan.success)
        assertEquals(listOf("other"), plan.groups)
        assertEquals(listOf("m1"), plan.clearMemberIds)
        assertEquals(listOf(ArchiveRoot.Group("other")), plan.archiveRoots)
    }
}

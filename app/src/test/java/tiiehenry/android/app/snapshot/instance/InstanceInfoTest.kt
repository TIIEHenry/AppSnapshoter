package tiiehenry.android.app.snapshot.instance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstanceInfoTest {

    private fun instance(id: String, name: String, account: String) = AppInstance(
        id = id,
        name = name,
        account = account,
        note = "",
        createdAt = 1L,
        updatedAt = 2L,
    )

    @Test
    fun check_requiresNameAndKeepsAccount() {
        val ok = InstanceNames.check("  工作  ", " a@b ", "", emptyList(), null)
        val info = (ok as InstanceInfoCheck.Ok).info
        assertEquals("工作", info.name)
        assertEquals("a@b", info.account)
        assertTrue(InstanceNames.check(" ", "", "", emptyList(), null) is InstanceInfoCheck.NameInvalid)
        assertTrue(InstanceNames.check("a/b", "", "", emptyList(), null) is InstanceInfoCheck.NameInvalid)
    }

    @Test
    fun check_rejectsDuplicateNameAndAccount() {
        val existing = listOf(instance("id1", "工作", "alice"))
        assertTrue(
            InstanceNames.check("工作", "bob", "", existing, null) is InstanceInfoCheck.NameTaken
        )
        assertTrue(
            InstanceNames.check("生活", "alice", "", existing, null) is InstanceInfoCheck.AccountTaken
        )
        val same = InstanceNames.check("工作", "alice", "备注", existing, "id1")
        assertTrue(same is InstanceInfoCheck.Ok)
    }

    @Test
    fun slotId_hidesCommitDirs() {
        assertTrue(InstanceNames.isSlotId("abc123"))
        assertFalse(InstanceNames.isSlotId("abc123.committing"))
        assertFalse(InstanceNames.isSlotId("abc123.previous"))
        assertEquals("abc123", InstanceNames.baseId("abc123"))
        assertEquals("abc123", InstanceNames.baseId("abc123.committing"))
        assertEquals("abc123", InstanceNames.baseId("abc123.previous"))
        assertEquals(null, InstanceNames.baseId("abc123.other"))
    }

    @Test
    fun meta_roundTrip_keepsAccount() {
        val original = AppInstance("abc123", "工作", "alice", "公司", 10L, 20L)
        assertEquals(original, InstanceMeta.decode(InstanceMeta.encode(original)))
    }
}

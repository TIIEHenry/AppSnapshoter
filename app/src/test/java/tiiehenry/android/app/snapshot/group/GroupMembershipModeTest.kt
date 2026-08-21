package tiiehenry.android.app.snapshot.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupMembershipModeTest {

    @Test
    fun fromStorage_defaultsToExclusive() {
        assertEquals(GroupMembershipMode.EXCLUSIVE, GroupMembershipMode.fromStorage(null))
        assertEquals(GroupMembershipMode.EXCLUSIVE, GroupMembershipMode.fromStorage(""))
        assertEquals(GroupMembershipMode.EXCLUSIVE, GroupMembershipMode.fromStorage("exclusive"))
        assertEquals(GroupMembershipMode.EXCLUSIVE, GroupMembershipMode.fromStorage("other"))
    }

    @Test
    fun fromStorage_sharedCaseInsensitive() {
        assertEquals(GroupMembershipMode.SHARED, GroupMembershipMode.fromStorage("shared"))
        assertEquals(GroupMembershipMode.SHARED, GroupMembershipMode.fromStorage("SHARED"))
        assertEquals(GroupMembershipMode.SHARED, GroupMembershipMode.fromStorage("Shared"))
    }

    @Test
    fun toStorage_roundTrip() {
        assertEquals(
            GroupMembershipMode.SHARED,
            GroupMembershipMode.fromStorage(GroupMembershipMode.SHARED.toStorage())
        )
        assertEquals(
            GroupMembershipMode.EXCLUSIVE,
            GroupMembershipMode.fromStorage(GroupMembershipMode.EXCLUSIVE.toStorage())
        )
    }
}

class PackageOpGuardTest {

    @Test
    fun globalBatch_blocksSecondBatchAndPackageOp() {
        val guard = PackageOpGuard()
        assertTrue(guard.tryBeginGlobalBatch())
        assertFalse(guard.tryBeginGlobalBatch())
        assertFalse(guard.tryBeginPackageOp("/a/pkg"))
        assertTrue(guard.isBusy())
        assertTrue(guard.isBusy("/a/pkg"))
        guard.endGlobalBatch()
        assertFalse(guard.isBusy())
        assertTrue(guard.tryBeginPackageOp("/a/pkg"))
    }

    @Test
    fun packageOp_blocksSameDirAndGlobalBatch() {
        val guard = PackageOpGuard()
        assertTrue(guard.tryBeginPackageOp("/data/com.example"))
        assertFalse(guard.tryBeginPackageOp("/data/com.example/"))
        assertFalse(guard.tryBeginGlobalBatch())
        assertTrue(guard.tryBeginPackageOp("/data/other"))
        guard.endPackageOp("/data/com.example")
        guard.endPackageOp("/data/other")
        assertTrue(guard.tryBeginGlobalBatch())
        guard.endGlobalBatch()
    }
}

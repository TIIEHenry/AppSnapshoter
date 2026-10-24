package tiiehenry.android.snapshot.provider.service.handler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionApplyTest {
    @Test fun `a zero grant result fails and does not set the op mode`() {
        var called = false
        val applied = applyAppPermission(0, op = 12, mode = 4, opNone = -1) {
            called = true
            true
        }
        assertFalse(applied)
        assertFalse(called)
    }

    @Test fun `a failed setOpsMode fails the permission`() {
        val applied = applyAppPermission(1, op = 12, mode = 4, opNone = -1) { false }
        assertFalse(applied)
    }

    @Test fun `a missing op mode is not written back as allowed`() {
        assertEquals(APP_OP_MODE_UNREAD, backedUpOpMode(emptyMap(), 12))
        assertEquals(APP_OP_MODE_UNREAD, backedUpOpMode(null, 12))
        var called = false
        val applied = applyAppPermission(
            1,
            op = 12,
            mode = backedUpOpMode(emptyMap(), 12),
            opNone = -1
        ) {
            called = true
            true
        }
        assertTrue(applied)
        assertFalse(called)
    }

    @Test fun `an unchangeable permission still succeeds when the op mode is restored`() {
        var seen = -1
        val applied = applyAppPermission(-1, op = 12, mode = 4, opNone = -1) {
            seen = it
            true
        }
        assertTrue(applied)
        assertEquals(4, seen)
    }

    @Test fun `a stored non-zero mode is written back unchanged`() {
        assertEquals(5, backedUpOpMode(mapOf(12 to 5), 12))
        var seen = -1
        assertTrue(applyAppPermission(1, op = 12, mode = 5, opNone = -1) {
            seen = it
            true
        })
        assertEquals(5, seen)
    }

    @Test fun `a stored allowed mode is distinct from an unread mode`() {
        assertEquals(0, backedUpOpMode(mapOf(12 to 0), 12))
        var seen = -1
        assertTrue(applyAppPermission(1, op = 12, mode = 0, opNone = -1) {
            seen = it
            true
        })
        assertEquals(0, seen)
    }
}

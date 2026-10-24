package tiiehenry.android.app.snapshot.archive.restore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionRestoreTest {
    private fun write(
        permissions: List<ArchivedPermission>,
        fixedNames: MutableList<String> = mutableListOf(),
        uid: Int = 10086,
        userPresent: Boolean = true,
        resetAppOps: () -> Boolean = { true },
        setOpsMode: (Int, Int) -> Boolean = { _, _ -> true },
        grant: (String) -> Unit = {},
        revoke: (String) -> Unit = {},
        isUnchangeable: ((Throwable) -> Boolean)? = null,
        opNone: Int = -1,
    ): List<String> = writeArchivedPermissions(
        permissions = permissions,
        fixedNames = fixedNames,
        uid = uid,
        userPresent = userPresent,
        packageName = "com.example",
        resetAppOps = resetAppOps,
        setOpsMode = setOpsMode,
        grant = grant,
        revoke = revoke,
        isUnchangeable = isUnchangeable ?: { error ->
            isUnchangeablePermissionType(error is android.os.RemoteException, error.message)
        },
        opNone = opNone,
    )

    @Test fun `fixed permission still sets ops mode after reset`() {
        val events = mutableListOf<String>()
        write(
            permissions = listOf(
                ArchivedPermission("SYSTEM_ALERT_WINDOW", granted = true, op = 24, mode = 1),
                ArchivedPermission("CAMERA", granted = false, op = 26, mode = 2),
            ),
            fixedNames = mutableListOf("SYSTEM_ALERT_WINDOW"),
            resetAppOps = { events += "reset"; true },
            setOpsMode = { op, mode -> events += "mode:$op:$mode"; true },
            grant = { events += "grant:$it" },
            revoke = { events += "revoke:$it" },
        )
        assertEquals(listOf("reset", "mode:24:1", "mode:26:2", "revoke:CAMERA"), events)
    }

    @Test fun `op none does not set ops mode`() {
        val modes = mutableListOf<Int>()
        val grants = mutableListOf<String>()
        write(
            permissions = listOf(ArchivedPermission("INTERNET", granted = true, op = -1, mode = 0)),
            setOpsMode = { op, _ -> modes += op; true },
            grant = { grants += it },
        )
        assertTrue(modes.isEmpty())
        assertEquals(listOf("INTERNET"), grants)
    }

    @Test fun `set ops mode failure fails permission restore`() {
        var granted = false
        assertThrows(IllegalStateException::class.java) {
            write(
                permissions = listOf(ArchivedPermission("CAMERA", granted = true, op = 26, mode = 1)),
                setOpsMode = { _, _ -> throw IllegalStateException("mode") },
                grant = { granted = true },
            )
        }
        assertFalse(granted)
    }

    @Test fun `false reset app ops result fails permission restore`() {
        var granted = false
        assertThrows(IllegalStateException::class.java) {
            write(
                permissions = listOf(ArchivedPermission("CAMERA", granted = true, op = 26, mode = 1)),
                resetAppOps = { false },
                grant = { granted = true },
            )
        }
        assertFalse(granted)
    }

    @Test fun `false set ops mode result fails permission restore`() {
        var granted = false
        assertThrows(IllegalStateException::class.java) {
            write(
                permissions = listOf(ArchivedPermission("CAMERA", granted = true, op = 26, mode = 1)),
                setOpsMode = { _, _ -> false },
                grant = { granted = true },
            )
        }
        assertFalse(granted)
    }

    @Test fun `ordinary grant failure fails permission restore`() {
        val events = mutableListOf<String>()
        assertThrows(IllegalStateException::class.java) {
            write(
                permissions = listOf(ArchivedPermission("CAMERA", granted = true, op = 26, mode = 1)),
                resetAppOps = { events += "reset"; true },
                setOpsMode = { _, _ -> events += "mode"; true },
                grant = { throw IllegalStateException("denied") },
            )
        }
        assertEquals(listOf("reset", "mode"), events)
    }

    @Test fun `non remote grant failure is not recorded as an unchangeable type`() {
        val fixed = mutableListOf<String>()
        assertThrows(IllegalStateException::class.java) {
            write(
                permissions = listOf(ArchivedPermission("READ", granted = true, op = 4, mode = 0)),
                fixedNames = fixed,
                grant = { throw IllegalStateException("not a changeable permission type") },
            )
        }
        assertTrue(fixed.isEmpty())
    }

    @Test fun `unchangeable permission type is recorded after its mode is written`() {
        val events = mutableListOf<String>()
        val fixed = mutableListOf<String>()
        val recorded = write(
            permissions = listOf(ArchivedPermission("READ_PHONE_STATE", granted = true, op = 51, mode = 0)),
            fixedNames = fixed,
            resetAppOps = { events += "reset"; true },
            setOpsMode = { op, mode -> events += "mode:$op:$mode"; true },
            grant = {
                events += "grant:$it"
                throw IllegalStateException("not a changeable permission type")
            },
            isUnchangeable = { isUnchangeablePermissionType(true, it.message) },
        )
        assertEquals(listOf("reset", "mode:51:0", "grant:READ_PHONE_STATE"), events)
        assertEquals(listOf("READ_PHONE_STATE"), recorded)
        assertEquals(listOf("READ_PHONE_STATE"), fixed)
        assertTrue(isUnchangeablePermissionType(true, "not a changeable permission type"))
        assertFalse(isUnchangeablePermissionType(true, "denied"))
        assertFalse(isUnchangeablePermissionType(false, "not a changeable permission type"))
        assertFalse(isUnchangeablePermissionType(true, null))
    }

    @Test fun `empty permissions skip reset`() {
        var reset = false
        val recorded = write(
            permissions = emptyList(),
            uid = -1,
            userPresent = false,
            resetAppOps = { reset = true; true },
        )
        assertTrue(recorded.isEmpty())
        assertFalse(reset)
    }

    @Test fun `missing uid or user fails permission restore`() {
        val permission = listOf(ArchivedPermission("CAMERA", granted = true, op = 26, mode = 1))
        var reset = false
        assertThrows(IllegalStateException::class.java) {
            write(permissions = permission, uid = -1, userPresent = true, resetAppOps = { reset = true; true })
        }
        assertThrows(IllegalStateException::class.java) {
            write(permissions = permission, uid = 10, userPresent = false, resetAppOps = { reset = true; true })
        }
        assertFalse(reset)
    }
}

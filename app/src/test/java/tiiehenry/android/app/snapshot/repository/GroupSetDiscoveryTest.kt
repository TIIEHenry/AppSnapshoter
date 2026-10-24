package tiiehenry.android.app.snapshot.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tiiehenry.android.snapshot.fs.IFileType

class GroupSetDiscoveryTest {

    @Test
    fun `listDir throw keeps groups mmkv and group order`() {
        val result = discoverGroupSetMembers(
            setPath = "/backup/set",
            groups = listOf("keep"),
            pathsById = mapOf("keep" to "/backup/set/keep"),
            groupOrder = listOf("keep"),
            listDir = { error("io") },
            fileType = { IFileType.TYPE_DIR },
            groupJsonExists = { false },
        )

        assertFalse(result.committed)
        assertEquals(listOf("keep"), result.groups)
        assertTrue(result.removedIds.isEmpty())
        assertTrue(result.added.isEmpty())
        assertEquals(listOf("keep"), result.groupOrder)
    }

    @Test
    fun `listDir null keeps groups mmkv and group order`() {
        val result = discoverGroupSetMembers(
            setPath = "/backup/set",
            groups = listOf("keep"),
            pathsById = mapOf("keep" to "/backup/set/keep"),
            groupOrder = listOf("keep"),
            listDir = { null },
            fileType = { IFileType.TYPE_DIR },
            groupJsonExists = { false },
        )

        assertFalse(result.committed)
        assertEquals(listOf("keep"), result.groups)
        assertTrue(result.removedIds.isEmpty())
        assertTrue(result.added.isEmpty())
        assertEquals(listOf("keep"), result.groupOrder)
    }

    @Test
    fun `fileType throw does not unregister other members or save group order`() {
        val result = discoverGroupSetMembers(
            setPath = "/backup/set",
            groups = listOf("keep"),
            pathsById = mapOf("keep" to "/backup/set/keep"),
            groupOrder = listOf("keep"),
            listDir = { listOf("keep", "fresh", "boom") },
            fileType = { path ->
                if (path.endsWith("/boom")) error("stat")
                IFileType.TYPE_DIR
            },
            groupJsonExists = { false },
        )

        assertFalse(result.committed)
        assertEquals(listOf("keep"), result.groups)
        assertTrue(result.removedIds.isEmpty())
        assertTrue(result.added.isEmpty())
        assertEquals(listOf("keep"), result.groupOrder)
    }

    @Test
    fun `missing basename unregisters that member`() {
        val result = discoverGroupSetMembers(
            setPath = "/backup/set",
            groups = listOf("keep", "gone", "outside"),
            pathsById = mapOf(
                "keep" to "/backup/set/keep",
                "gone" to "/backup/set/gone",
                "outside" to "/backup/other/outside",
            ),
            groupOrder = listOf("keep", "gone"),
            listDir = { listOf("keep") },
            fileType = { IFileType.TYPE_DIR },
            groupJsonExists = { false },
        )

        assertTrue(result.committed)
        assertEquals(listOf("gone"), result.removedIds)
        assertEquals(listOf("keep", "outside"), result.groups)
        assertEquals(listOf("keep"), result.groupOrder)
        assertTrue(result.added.isEmpty())
    }

    @Test
    fun `dot directory with group json is registered and stays on rescan`() {
        val first = discoverGroupSetMembers(
            setPath = "/backup/set",
            groups = emptyList(),
            pathsById = emptyMap(),
            groupOrder = emptyList(),
            listDir = { listOf(".hidden") },
            fileType = { IFileType.TYPE_DIR },
            groupJsonExists = { it.endsWith("/.hidden/group.json") },
            allocateId = { "hid1" },
        )

        assertTrue(first.committed)
        assertEquals(listOf("hid1"), first.groups)
        assertEquals(".hidden", first.added.single().second.basename)
        assertTrue(first.added.single().second.hasGroupJson)
        assertEquals(listOf(".hidden"), first.groupOrder)
        assertTrue(first.removedIds.isEmpty())

        val second = discoverGroupSetMembers(
            setPath = "/backup/set",
            groups = first.groups,
            pathsById = mapOf("hid1" to first.added.single().second.path),
            groupOrder = first.groupOrder,
            listDir = { listOf(".hidden") },
            fileType = { IFileType.TYPE_DIR },
            groupJsonExists = { it.endsWith("/.hidden/group.json") },
            allocateId = { error("already registered") },
        )

        assertTrue(second.committed)
        assertEquals(listOf("hid1"), second.groups)
        assertTrue(second.removedIds.isEmpty())
        assertTrue(second.added.isEmpty())
        assertEquals(listOf(".hidden"), second.groupOrder)
    }

    @Test
    fun `package-like directory without group json is not registered`() {
        val result = discoverGroupSetMembers(
            setPath = "/backup/set",
            groups = emptyList(),
            pathsById = emptyMap(),
            groupOrder = emptyList(),
            listDir = { listOf("com.example.app", "readme.txt", "notes") },
            fileType = { path ->
                if (path.endsWith("/readme.txt")) IFileType.TYPE_FILE else IFileType.TYPE_DIR
            },
            groupJsonExists = { false },
            allocateId = { "n1" },
        )

        assertTrue(result.committed)
        assertEquals(listOf("notes"), result.added.map { it.second.basename })
        assertFalse(result.added.single().second.hasGroupJson)
        assertEquals(listOf("notes"), result.groupOrder)
        assertEquals(listOf("n1"), result.groups)
    }
}

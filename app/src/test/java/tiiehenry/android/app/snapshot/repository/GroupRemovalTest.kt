package tiiehenry.android.app.snapshot.repository

import org.junit.Assert.*
import org.junit.Test

class GroupRemovalTest {
    @Test
    fun `delete uses original group directory before clearing its registration`() {
        val defaultRoot = "/storage/emulated/0/Android/snapshot"
        val originalPath = "$defaultRoot/group1"
        var registeredPath: String? = originalPath
        val directories = mutableSetOf(defaultRoot, originalPath, "$defaultRoot/group2")
        val deletedPaths = mutableListOf<String>()

        val removed = removeRegisteredGroup(
            path = registeredPath ?: defaultRoot,
            deleteFiles = true,
            exists = directories::contains,
            delete = { path ->
                assertEquals(originalPath, registeredPath)
                deletedPaths += path
                directories.remove(path)
            },
            unregister = { registeredPath = null },
        )

        assertTrue(removed)
        assertNull(registeredPath)
        assertEquals(listOf(originalPath), deletedPaths)
        assertEquals(setOf(defaultRoot, "$defaultRoot/group2"), directories)
    }

    @Test
    fun `failed deletion keeps group registration and configuration`() {
        var registered = true
        val config = mutableMapOf("rootPath" to "/backups/group", "isCollapsed" to "true")

        val removed = removeRegisteredGroup(
            path = config.getValue("rootPath"),
            deleteFiles = true,
            exists = { true },
            delete = { false },
            unregister = {
                registered = false
                config.clear()
            },
        )

        assertFalse(removed)
        assertTrue(registered)
        assertEquals("/backups/group", config["rootPath"])
        assertEquals("true", config["isCollapsed"])
    }

    @Test
    fun `throwing deletion leaves registration available for retry`() {
        var registered = true
        val result = runCatching {
            removeRegisteredGroup(
                path = "/backups/group",
                deleteFiles = true,
                exists = { true },
                delete = { error("storage disconnected") },
                unregister = { registered = false },
            )
        }

        assertTrue(result.isFailure)
        assertTrue(registered)
    }

    @Test
    fun `configuration-only removal never accesses files`() {
        var registered = true
        assertTrue(removeRegisteredGroup(
            path = "/backups/group",
            deleteFiles = false,
            exists = { error("configuration-only removal must not inspect storage") },
            delete = { error("configuration-only removal must not delete storage") },
            unregister = { registered = false },
        ))
        assertFalse(registered)
    }

    @Test
    fun `already missing directory can be unregistered`() {
        var registered = true
        assertTrue(removeRegisteredGroup(
            path = "/backups/missing",
            deleteFiles = true,
            exists = { false },
            delete = { error("missing directory need not be deleted") },
            unregister = { registered = false },
        ))
        assertFalse(registered)
    }
}

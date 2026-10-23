package tiiehenry.android.app.snapshot.config

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GroupConfigStateTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `registering existing directory preserves snapshot exclusions actions and membership`() {
        val directory = temporaryFolder.newFolder("imported")
        File(directory, ConfigFiles.SHOT_CONFIG_FILE).writeText(
            """{"enabled":true,"permission":false,"items":["data"]}"""
        )
        File(directory, ConfigFiles.EXCLUDE_CONFIG_FILE).writeText("""{"data":["cache/**"]}""")
        File(directory, ConfigFiles.ACTION_CONFIG_FILE).writeText(
            """{"enabled":true,"compressLevel":12,"uninstallArchived":true}"""
        )
        val groupFile = File(directory, ConfigFiles.GROUP_CONFIG_FILE)
        val originalGroupJson = """{"name":"Old","userId":10,"membershipMode":"shared","lockedList":["com.example.app"],"sortConfig":{"sortOrder":["com.example.app"]}}"""
        groupFile.writeText(originalGroupJson)

        val state = GroupConfigState.forRegistration(directory.path, "Imported", 11)

        assertTrue(state.shot.enabled)
        assertFalse(state.shot.permission)
        assertEquals(setOf("data"), state.shot.items)
        assertEquals(listOf("cache/**"), state.exclude.getExcludePatternsForItem("data"))
        assertEquals(12, state.action.compressLevel)
        assertTrue(state.action.isUninstallArchived)
        assertEquals("shared", state.group.membershipMode)
        assertEquals(listOf("com.example.app"), state.group.lockedList)
        assertEquals(listOf("com.example.app"), state.group.sortConfig.sortOrder)
        assertEquals("Imported", state.group.name)
        assertEquals(11, state.group.userId)
        assertEquals(originalGroupJson, groupFile.readText())
    }

    @Test
    fun `directory without group json still imports its other configuration files`() {
        val directory = temporaryFolder.newFolder("without-group-json")
        File(directory, ConfigFiles.EXCLUDE_CONFIG_FILE).writeText("""{"user":["private/**"]}""")

        val state = GroupConfigState.forRegistration(directory.path, "Imported", 10)

        assertEquals(listOf("private/**"), state.exclude.getExcludePatternsForItem("user"))
        assertEquals("Imported", state.group.name)
        assertEquals(10, state.group.userId)
    }

    @Test
    fun `new directory does not inherit settings from a previously loaded group`() {
        val oldDirectory = temporaryFolder.newFolder("old")
        File(oldDirectory, ConfigFiles.GROUP_CONFIG_FILE).writeText(
            """{"membershipMode":"shared","lockedList":["com.other.app"]}"""
        )
        GroupConfigState.load(oldDirectory.path)
        val newDirectory = temporaryFolder.newFolder("new")

        val state = GroupConfigState.forRegistration(newDirectory.path, "New", 0)

        assertEquals("exclusive", state.group.membershipMode)
        assertTrue(state.group.lockedList.isEmpty())
        assertEquals("New", state.group.name)
    }
}

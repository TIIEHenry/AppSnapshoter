package tiiehenry.android.app.snapshot.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class GroupConfigTest : ConfigJvmTest() {
    @Test
    fun `save leaves files unchanged when the temp write fails after open`() {
        val dir = temporaryFolder.newFolder("truncate")
        writeGroupFiles(
            dir,
            items = """{"enabled":true,"permission":false,"items":["data"]}""",
            excludes = """{"data":["cache/**"]}""",
            action = """{"enabled":true,"compressLevel":12,"uninstallArchived":true}""",
            group = """{"name":"Keep","lockedList":["com.keep.app"]}""",
        )
        val before = snapshotFiles(dir, GROUP_CONFIG_FILE_NAMES)
        val config = groupAt(dir)
        config.groupConfigData.name = "Changed"
        ConfigFileStore.afterTempOpened = { throw IOException("fail after open") }

        assertThrows(IOException::class.java) { config.save() }

        assertSnapshot(dir, before)
        assertNoTempFiles(dir)
    }

    @Test
    fun `registerPath addToLockedList and save fail without changing files when an existing config cannot be parsed`() {
        val dir = temporaryFolder.newFolder("unreadable")
        writeGroupFiles(
            dir,
            items = "{",
            excludes = """{"data":["cache/**"]}""",
            action = """{"enabled":true,"compressLevel":12,"uninstallArchived":true}""",
            group = """{"name":"Keep","userId":4,"membershipMode":"shared","lockedList":["com.keep.app"]}""",
        )
        val before = snapshotFiles(dir, GROUP_CONFIG_FILE_NAMES)
        val config = GroupConfig("broken", MemoryConfigPrefs())

        assertThrows(IllegalStateException::class.java) {
            config.registerPath(dir.absolutePath, "Imported", 11)
        }
        assertThrows(IllegalStateException::class.java) { config.save() }
        assertThrows(IllegalStateException::class.java) { config.addToLockedList("com.example.app") }
        assertThrows(IllegalStateException::class.java) { config.removeFromLockedList("com.keep.app") }

        assertSnapshot(dir, before)
        assertFalse(File(globalRoot, ConfigFiles.GROUP_CONFIG_FILE).exists())
        assertNoTempFiles(dir)
    }

    @Test
    fun `missing files load as defaults and save creates them`() {
        val dir = temporaryFolder.newFolder("empty-group")
        val config = groupAt(dir)
        assertFalse(File(dir, ConfigFiles.GROUP_CONFIG_FILE).exists())
        assertFalse(config.shotConfig.enabled)
        assertTrue(config.shotConfig.permission)
        assertEquals(CompressItems.all, config.shotConfig.items)
        assertTrue(config.groupConfigData.lockedList.isEmpty())
        assertEquals("exclusive", config.groupConfigData.membershipMode)

        config.groupConfigData.name = "Created"
        config.save()

        assertTrue(File(dir, ConfigFiles.SHOT_CONFIG_FILE).isFile)
        assertTrue(File(dir, ConfigFiles.EXCLUDE_CONFIG_FILE).isFile)
        assertTrue(File(dir, ConfigFiles.ACTION_CONFIG_FILE).isFile)
        val group = GroupConfigData.fromJson(File(dir, ConfigFiles.GROUP_CONFIG_FILE).readText())
        assertEquals("Created", group.name)
        assertTrue(group.lockedList.isEmpty())
        val shot = ShotConfig.fromJson(File(dir, ConfigFiles.SHOT_CONFIG_FILE).readText())
        assertEquals(CompressItems.all, shot.items)
    }

    @Test
    fun `setting rootPath then save keeps the target directory contents`() {
        val target = temporaryFolder.newFolder("target-group")
        writeGroupFiles(globalRoot, GLOBAL_ITEMS, GLOBAL_EXCLUDES, GLOBAL_ACTION, GLOBAL_GROUP)
        writeGroupFiles(target, TARGET_ITEMS, TARGET_EXCLUDES, TARGET_ACTION, TARGET_GROUP)
        val globalBefore = snapshotFiles(globalRoot, GROUP_CONFIG_FILE_NAMES)
        val config = GroupConfig("move", MemoryConfigPrefs())
        assertEquals("FromGlobal", config.groupConfigData.name)
        assertEquals(listOf("com.global.app"), config.groupConfigData.lockedList)

        config.rootPath = target.absolutePath
        assertEquals("FromTarget", config.groupConfigData.name)
        config.save()

        assertSnapshot(globalRoot, globalBefore)
        val group = GroupConfigData.fromJson(File(target, ConfigFiles.GROUP_CONFIG_FILE).readText())
        assertEquals("FromTarget", group.name)
        assertEquals(4, group.userId)
        assertEquals("shared", group.membershipMode)
        assertEquals(listOf("com.target.app"), group.lockedList)
        assertEquals(3, group.sortConfig.sortType)
        assertEquals(listOf("com.target.app"), group.sortConfig.sortOrder)
        val shot = ShotConfig.fromJson(File(target, ConfigFiles.SHOT_CONFIG_FILE).readText())
        assertTrue(shot.enabled)
        assertFalse(shot.permission)
        assertEquals(setOf("data"), shot.items)
        val excludes = ExcludeConfig.fromJson(File(target, ConfigFiles.EXCLUDE_CONFIG_FILE).readText())
        assertEquals(listOf("secret/**"), excludes.getExcludePatternsForItem("user"))
        val action = ActionConfig.fromJson(File(target, ConfigFiles.ACTION_CONFIG_FILE).readText())
        assertTrue(action.enabled)
        assertEquals(12, action.compressLevel)
        assertTrue(action.isUninstallArchived)
        val groupText = File(target, ConfigFiles.GROUP_CONFIG_FILE).readText()
        assertFalse(groupText.contains("FromGlobal"))
        assertFalse(groupText.contains("com.global.app"))
    }

    @Test
    fun `addToLockedList after rootPath change writes the target list plus the new package`() {
        val target = temporaryFolder.newFolder("lock-target")
        writeGroupFiles(globalRoot, GLOBAL_ITEMS, GLOBAL_EXCLUDES, GLOBAL_ACTION, GLOBAL_GROUP)
        writeGroupFiles(target, TARGET_ITEMS, TARGET_EXCLUDES, TARGET_ACTION, TARGET_GROUP)
        val globalBefore = snapshotFiles(globalRoot, GROUP_CONFIG_FILE_NAMES)
        val config = GroupConfig("lock", MemoryConfigPrefs())

        config.rootPath = target.absolutePath
        config.addToLockedList("com.added.app")

        assertSnapshot(globalRoot, globalBefore)
        val group = GroupConfigData.fromJson(File(target, ConfigFiles.GROUP_CONFIG_FILE).readText())
        assertEquals(listOf("com.target.app", "com.added.app"), group.lockedList)
        assertEquals("FromTarget", group.name)
        assertEquals("shared", group.membershipMode)
        val excludes = ExcludeConfig.fromJson(File(target, ConfigFiles.EXCLUDE_CONFIG_FILE).readText())
        assertEquals(listOf("secret/**"), excludes.getExcludePatternsForItem("user"))
        val shot = ShotConfig.fromJson(File(target, ConfigFiles.SHOT_CONFIG_FILE).readText())
        assertEquals(setOf("data"), shot.items)
    }

    @Test
    fun `registerPath keeps existing settings and writes the registered name`() {
        val target = temporaryFolder.newFolder("register")
        writeGroupFiles(target, TARGET_ITEMS, TARGET_EXCLUDES, TARGET_ACTION, TARGET_GROUP)
        val config = GroupConfig("register", MemoryConfigPrefs())

        config.registerPath(target.absolutePath, "Imported", 11)

        val group = GroupConfigData.fromJson(File(target, ConfigFiles.GROUP_CONFIG_FILE).readText())
        assertEquals("Imported", group.name)
        assertEquals(11, group.userId)
        assertEquals("shared", group.membershipMode)
        assertEquals(listOf("com.target.app"), group.lockedList)
        val excludes = ExcludeConfig.fromJson(File(target, ConfigFiles.EXCLUDE_CONFIG_FILE).readText())
        assertEquals(listOf("secret/**"), excludes.getExcludePatternsForItem("user"))
        val shot = ShotConfig.fromJson(File(target, ConfigFiles.SHOT_CONFIG_FILE).readText())
        assertEquals(setOf("data"), shot.items)
        assertFalse(shot.permission)
    }

    @Test
    fun `blank rootPath reads and writes the global root like a missing key`() {
        val other = temporaryFolder.newFolder("other-group")
        File(globalRoot, ConfigFiles.GROUP_CONFIG_FILE).writeText("""{"name":"FromGlobal"}""")
        File(other, ConfigFiles.GROUP_CONFIG_FILE).writeText("""{"name":"FromOther"}""")
        val missing = GroupConfig("missing", MemoryConfigPrefs())
        assertEquals(globalRoot.absolutePath, missing.rootPath)
        assertEquals("FromGlobal", missing.groupConfigData.name)

        val storedBlank = MemoryConfigPrefs()
        storedBlank.encode(GroupConfig.KEY_ROOT_PATH, "")
        val blank = GroupConfig("blank", storedBlank)
        assertEquals(globalRoot.absolutePath, blank.rootPath)
        assertEquals("FromGlobal", blank.groupConfigData.name)

        val storedSpace = MemoryConfigPrefs()
        storedSpace.encode(GroupConfig.KEY_ROOT_PATH, "   ")
        val spaced = GroupConfig("spaced", storedSpace)
        assertEquals(globalRoot.absolutePath, spaced.rootPath)
        assertEquals("FromGlobal", spaced.groupConfigData.name)

        missing.rootPath = "   "
        assertEquals(globalRoot.absolutePath, missing.rootPath)
        missing.rootPath = other.absolutePath
        assertEquals("FromOther", missing.groupConfigData.name)
        missing.rootPath = ""
        assertEquals(globalRoot.absolutePath, missing.rootPath)
        assertEquals("FromGlobal", missing.groupConfigData.name)
        missing.groupConfigData.name = "Updated"
        missing.save()

        assertEquals(
            "Updated",
            GroupConfigData.fromJson(File(globalRoot, ConfigFiles.GROUP_CONFIG_FILE).readText()).name,
        )
        assertEquals(
            "FromOther",
            GroupConfigData.fromJson(File(other, ConfigFiles.GROUP_CONFIG_FILE).readText()).name,
        )
    }

    @Test
    fun `null lockedList works as an empty list for lock queries`() {
        val dir = temporaryFolder.newFolder("null-lock")
        File(dir, ConfigFiles.GROUP_CONFIG_FILE).writeText("""{"lockedList":null}""")
        val config = groupAt(dir)

        assertNotNull(config.groupConfigData.lockedList)
        assertTrue(config.groupConfigData.lockedList.isEmpty())
        assertFalse(config.isLocked("a.b"))
        config.removeFromLockedList("a.b")
        config.addToLockedList("a.b")
        assertTrue(config.isLocked("a.b"))
        config.removeFromLockedList("a.b")
        assertFalse(config.isLocked("a.b"))
    }

    @Test
    fun `null sortConfig is replaced with a sort config`() {
        val parsed = GroupConfigData.fromJson("""{"sortConfig":null}""")
        assertNotNull(parsed.sortConfig)
        assertEquals(SortConfig.SORT_TYPE_DEFAULT, parsed.sortConfig.sortType)
        assertNotNull(parsed.sortConfig.sortOrder)

        val dir = temporaryFolder.newFolder("null-sort")
        File(dir, ConfigFiles.GROUP_CONFIG_FILE).writeText("""{"sortConfig":null}""")
        val config = groupAt(dir)
        assertNotNull(config.sortConfig)
        assertEquals(SortConfig.SORT_TYPE_DEFAULT, config.sortConfig.sortType)
    }

    @Test
    fun `explicit locked package stays locked`() {
        val parsed = GroupConfigData.fromJson("""{"lockedList":["a.b"]}""")
        assertEquals(listOf("a.b"), parsed.lockedList)

        val dir = temporaryFolder.newFolder("locked")
        File(dir, ConfigFiles.GROUP_CONFIG_FILE).writeText("""{"lockedList":["a.b"]}""")
        val config = groupAt(dir)
        assertTrue(config.isLocked("a.b"))
        assertFalse(config.isLocked("c.d"))
    }

    @Test
    fun `null json and missing fields keep a non-null locked list and sort config`() {
        val parsedNull = GroupConfigData.fromJson("null")
        assertNotNull(parsedNull.lockedList)
        assertTrue(parsedNull.lockedList.isEmpty())
        assertNotNull(parsedNull.sortConfig)

        val missing = GroupConfigData.fromJson("{}")
        assertNotNull(missing.lockedList)
        assertTrue(missing.lockedList.isEmpty())
        assertNotNull(missing.sortConfig)
        assertEquals(SortConfig.SORT_TYPE_DEFAULT, missing.sortConfig.sortType)
        assertEquals("exclusive", missing.membershipMode)
    }

    private fun groupAt(dir: File, id: String = "group"): GroupConfig {
        val prefs = MemoryConfigPrefs()
        prefs.encode(GroupConfig.KEY_ROOT_PATH, dir.absolutePath)
        return GroupConfig(id, prefs)
    }

    private companion object {
        const val GLOBAL_ITEMS = """{"enabled":false,"permission":true,"items":["apk"]}"""
        const val GLOBAL_EXCLUDES = """{"data":["cache/**"]}"""
        const val GLOBAL_ACTION = """{"enabled":false,"compressLevel":1}"""
        const val GLOBAL_GROUP =
            """{"name":"FromGlobal","userId":1,"membershipMode":"exclusive","lockedList":["com.global.app"],"sortConfig":{"sortType":1,"sortOrder":["com.global.app"]}}"""
        const val TARGET_ITEMS = """{"enabled":true,"permission":false,"items":["data"]}"""
        const val TARGET_EXCLUDES = """{"user":["secret/**"]}"""
        const val TARGET_ACTION = """{"enabled":true,"compressLevel":12,"uninstallArchived":true}"""
        const val TARGET_GROUP =
            """{"name":"FromTarget","userId":4,"membershipMode":"shared","lockedList":["com.target.app"],"sortConfig":{"sortType":3,"sortOrder":["com.target.app"]}}"""
    }
}

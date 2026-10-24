package tiiehenry.android.app.snapshot.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class GroupSetConfigTest : ConfigJvmTest() {
    @Test
    fun `save leaves the file unchanged when it cannot be parsed`() {
        val file = File(globalRoot, ConfigFiles.GROUP_SET_CONFIG_FILE)
        file.writeText("{")
        val before = file.readBytes()
        val config = GroupSetConfig("broken", MemoryConfigPrefs())

        assertThrows(IllegalStateException::class.java) { config.save() }

        assertTrue(file.readBytes().contentEquals(before))
        assertNoTempFiles(globalRoot)
    }

    @Test
    fun `save leaves the file unchanged when replace fails`() {
        val file = File(globalRoot, ConfigFiles.GROUP_SET_CONFIG_FILE)
        file.writeText("""{"name":"Keep","groupOrder":["a"]}""")
        val before = file.readBytes()
        val config = GroupSetConfig("replace", MemoryConfigPrefs())
        config.data.name = "Changed"
        ConfigFileStore.replaceTemp = { _, _ -> throw IOException("replace failed") }

        assertThrows(IOException::class.java) { config.save() }

        assertTrue(file.readBytes().contentEquals(before))
        assertNoTempFiles(globalRoot)
    }

    @Test
    fun `save leaves the file unchanged when the temp write fails after open`() {
        val file = File(globalRoot, ConfigFiles.GROUP_SET_CONFIG_FILE)
        file.writeText("""{"name":"Keep","groupOrder":["a"]}""")
        val before = file.readBytes()
        val config = GroupSetConfig("truncate", MemoryConfigPrefs())
        config.data.name = "Changed"
        ConfigFileStore.afterTempOpened = { throw IOException("fail after open") }

        assertThrows(IOException::class.java) { config.save() }

        assertTrue(file.readBytes().contentEquals(before))
        assertNoTempFiles(globalRoot)
    }

    @Test
    fun `missing file loads defaults and save creates it`() {
        val file = File(globalRoot, ConfigFiles.GROUP_SET_CONFIG_FILE)
        val config = GroupSetConfig("missing", MemoryConfigPrefs())
        assertFalse(file.exists())
        assertNull(config.data.name)
        assertTrue(config.data.groupOrder.isEmpty())

        config.data.name = "Created"
        config.save()

        assertTrue(file.isFile)
        val saved = GroupSetConfigData.fromJson(file.readText())
        assertEquals("Created", saved.name)
        assertTrue(saved.groupOrder.isEmpty())
    }

    @Test
    fun `changing rootPath then load uses the target file accent color`() {
        File(globalRoot, ConfigFiles.GROUP_SET_CONFIG_FILE).writeText(
            """{"name":"GlobalSet","accentColor":"#FF000001","groupOrder":["global"]}"""
        )
        val target = temporaryFolder.newFolder("set-target")
        File(target, ConfigFiles.GROUP_SET_CONFIG_FILE).writeText(
            """{"name":"TargetSet","accentColor":"#FF000002","groupOrder":["target"]}"""
        )
        val config = GroupSetConfig("set", MemoryConfigPrefs())
        assertEquals(0xFF000001.toInt(), config.accentColor)
        assertEquals("GlobalSet", config.data.name)

        config.rootPath = target.absolutePath
        config.load()

        assertEquals(0xFF000002.toInt(), config.accentColor)
        assertEquals("TargetSet", config.data.name)
        assertEquals(listOf("target"), config.data.groupOrder)
    }

    @Test
    fun `clearLocal then save does not create or modify the global groupset file`() {
        val file = File(globalRoot, ConfigFiles.GROUP_SET_CONFIG_FILE)
        val config = GroupSetConfig("cleared", MemoryConfigPrefs())
        config.data.name = "in-memory"
        config.data.groupOrder = arrayListOf("g1")
        config.clearLocal()

        assertThrows(IllegalStateException::class.java) { config.save() }
        assertFalse(file.exists())

        val raw = """{"name":"on-disk","groupOrder":["keep"]}"""
        file.writeText(raw)
        val again = GroupSetConfig("again", MemoryConfigPrefs())
        assertEquals("on-disk", again.data.name)
        again.clearLocal()
        assertThrows(IllegalStateException::class.java) { again.save() }
        assertEquals(raw, file.readText())
    }

    @Test
    fun `blank rootPath reads and writes the global root like a missing key`() {
        val other = temporaryFolder.newFolder("other-set")
        File(globalRoot, ConfigFiles.GROUP_SET_CONFIG_FILE).writeText(
            """{"name":"GlobalSet","groupOrder":["global"]}"""
        )
        File(other, ConfigFiles.GROUP_SET_CONFIG_FILE).writeText(
            """{"name":"OtherSet","groupOrder":["other"]}"""
        )
        val missing = GroupSetConfig("missing-key", MemoryConfigPrefs())
        assertEquals(globalRoot.absolutePath, missing.rootPath)
        assertEquals("GlobalSet", missing.data.name)

        val storedBlank = MemoryConfigPrefs()
        storedBlank.encode(GroupSetConfig.KEY_ROOT_PATH, "")
        val blank = GroupSetConfig("blank", storedBlank)
        assertEquals(globalRoot.absolutePath, blank.rootPath)
        assertEquals("GlobalSet", blank.data.name)

        val storedSpace = MemoryConfigPrefs()
        storedSpace.encode(GroupSetConfig.KEY_ROOT_PATH, " \t ")
        val spaced = GroupSetConfig("spaced", storedSpace)
        assertEquals(globalRoot.absolutePath, spaced.rootPath)
        assertEquals("GlobalSet", spaced.data.name)

        missing.rootPath = other.absolutePath
        assertEquals("OtherSet", missing.data.name)
        missing.rootPath = ""
        assertEquals(globalRoot.absolutePath, missing.rootPath)
        assertEquals("GlobalSet", missing.data.name)
        missing.data.name = "Updated"
        missing.save()

        assertEquals(
            "Updated",
            GroupSetConfigData.fromJson(File(globalRoot, ConfigFiles.GROUP_SET_CONFIG_FILE).readText()).name,
        )
        assertEquals(
            "OtherSet",
            GroupSetConfigData.fromJson(File(other, ConfigFiles.GROUP_SET_CONFIG_FILE).readText()).name,
        )
    }
}

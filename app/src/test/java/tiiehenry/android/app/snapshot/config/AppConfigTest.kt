package tiiehenry.android.app.snapshot.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class AppConfigTest : ConfigJvmTest() {
    @Test
    fun `saveExtraItems copies missing legacy files into the canonical directory and removes the legacy directory`() {
        val pkg = "com.example.legacy"
        val items = """{"enabled":true,"permission":false,"items":["data"]}"""
        val excludes = """{"data":["cache/**"]}"""
        val action = """{"enabled":true,"compressLevel":12,"uninstallArchived":true}"""
        val legacy = appLegacy(globalRoot, pkg)
        legacy.mkdirs()
        File(legacy, ConfigFiles.SHOT_CONFIG_FILE).writeText(items)
        File(legacy, ConfigFiles.EXCLUDE_CONFIG_FILE).writeText(excludes)
        File(legacy, ConfigFiles.ACTION_CONFIG_FILE).writeText(action)

        val config = AppConfig(pkg, 0)
        assertEquals(setOf("data"), config.shotConfig.items)
        assertFalse(config.shotConfig.permission)
        assertEquals(listOf("cache/**"), config.excludeConfig.getExcludePatternsForItem("data"))
        assertEquals(12, config.actionConfig.compressLevel)
        assertTrue(config.actionConfig.isUninstallArchived)

        config.saveExtraItems(listOf(ExtraCompressItem("maps", "/sdcard/Maps", emptyList(), true)))

        val canonical = appCanonical(globalRoot, pkg, 0)
        assertEquals(items, File(canonical, ConfigFiles.SHOT_CONFIG_FILE).readText())
        assertEquals(excludes, File(canonical, ConfigFiles.EXCLUDE_CONFIG_FILE).readText())
        assertEquals(action, File(canonical, ConfigFiles.ACTION_CONFIG_FILE).readText())
        val extra = File(canonical, ConfigFiles.EXTRA_CONFIG_FILE).readText()
        assertTrue(extra.contains("maps"))
        assertTrue(extra.contains("/sdcard/Maps"))
        assertFalse(legacy.exists())

        val reloaded = AppConfig(pkg, 0)
        assertEquals(setOf("data"), reloaded.shotConfig.items)
        assertEquals("maps", reloaded.extraItems.single().name)
        assertEquals("/sdcard/Maps", reloaded.extraItems.single().path)
    }

    @Test
    fun `refreshConfig keeps the values just saved on the same cached instance`() {
        val pkg = "com.example.refresh"
        val legacy = appLegacy(globalRoot, pkg)
        legacy.mkdirs()
        File(legacy, ConfigFiles.SHOT_CONFIG_FILE).writeText(
            """{"enabled":true,"permission":true,"items":["data"]}"""
        )
        val manager = AppConfigManager.getInstance()
        val config = manager.getConfig(pkg, 0)
        config.shotConfig.enabled = true
        config.shotConfig.permission = false
        config.shotConfig.items = hashSetOf("apk")
        config.save()

        legacy.mkdirs()
        File(legacy, ConfigFiles.SHOT_CONFIG_FILE).writeText(
            """{"enabled":true,"permission":true,"items":["poison"]}"""
        )
        manager.refreshConfig(pkg, 0)

        assertTrue(config.shotConfig.enabled)
        assertFalse(config.shotConfig.permission)
        assertEquals(setOf("apk"), config.shotConfig.items)
        val canonicalItems = File(appCanonical(globalRoot, pkg, 0), ConfigFiles.SHOT_CONFIG_FILE).readText()
        assertTrue(canonicalItems.contains("apk"))
        assertFalse(canonicalItems.contains("poison"))
        assertFalse(canonicalItems.contains("\"data\""))
    }

    @Test
    fun `reset restores defaults and deletes canonical and legacy config files`() {
        val pkg = "com.example.reset"
        val config = AppConfig(pkg, 0)
        config.shotConfig.enabled = true
        config.shotConfig.items = hashSetOf("data")
        config.save()
        val canonical = appCanonical(globalRoot, pkg, 0)
        val legacy = appLegacy(globalRoot, pkg)
        legacy.mkdirs()
        File(legacy, ConfigFiles.SHOT_CONFIG_FILE).writeText("poison")
        assertTrue(File(canonical, ConfigFiles.SHOT_CONFIG_FILE).isFile)

        config.reset()

        assertFalse(canonical.exists())
        assertFalse(legacy.exists())
        APP_CONFIG_FILE_NAMES.forEach { name ->
            assertFalse(File(canonical, name).exists())
            assertFalse(File(legacy, name).exists())
        }
        val fresh = AppConfig(pkg, 0)
        assertFalse(fresh.shotConfig.enabled)
        assertTrue(fresh.shotConfig.permission)
        assertEquals(CompressItems.all, fresh.shotConfig.items)
        assertTrue(fresh.excludeConfig.itemExcludePatternsMap.isEmpty())
        assertFalse(fresh.actionConfig.enabled)
        assertEquals(3, fresh.actionConfig.compressLevel)
        assertTrue(fresh.extraItems.isEmpty())
    }

    @Test
    fun `changing the global root makes load and save use the new root`() {
        val pkg = "com.example.rootmove"
        val oldRoot = globalRoot
        val newRoot = temporaryFolder.newFolder("new-root")
        val oldItems = """{"enabled":true,"permission":false,"items":["old-marker"]}"""
        val newItems = """{"enabled":false,"permission":true,"items":["new-marker"]}"""
        val oldDir = appCanonical(oldRoot, pkg, 0)
        val newDir = appCanonical(newRoot, pkg, 0)
        oldDir.mkdirs()
        newDir.mkdirs()
        File(oldDir, ConfigFiles.SHOT_CONFIG_FILE).writeText(oldItems)
        File(newDir, ConfigFiles.SHOT_CONFIG_FILE).writeText(newItems)

        val config = AppConfig(pkg, 0)
        assertEquals(setOf("old-marker"), config.shotConfig.items)
        assertTrue(config.shotConfig.enabled)

        ConfigEnvironment.setGlobalRootPathForTest { newRoot.absolutePath }
        config.load()
        assertEquals(setOf("new-marker"), config.shotConfig.items)
        assertFalse(config.shotConfig.enabled)
        config.shotConfig.enabled = true
        config.save()

        val saved = ShotConfig.fromJson(File(newDir, ConfigFiles.SHOT_CONFIG_FILE).readText())
        assertEquals(setOf("new-marker"), saved.items)
        assertTrue(saved.enabled)
        assertFalse(saved.items.contains("old-marker"))
        assertEquals(oldItems, File(oldDir, ConfigFiles.SHOT_CONFIG_FILE).readText())

        ConfigEnvironment.setGlobalRootPathForTest { oldRoot.absolutePath }
        val stale = AppConfig(pkg, 0)
        assertEquals(setOf("old-marker"), stale.shotConfig.items)
        ConfigEnvironment.setGlobalRootPathForTest { newRoot.absolutePath }
        stale.save()
        val afterStaleSave = ShotConfig.fromJson(File(newDir, ConfigFiles.SHOT_CONFIG_FILE).readText())
        assertEquals(setOf("new-marker"), afterStaleSave.items)
        assertTrue(afterStaleSave.enabled)
        assertEquals(oldItems, File(oldDir, ConfigFiles.SHOT_CONFIG_FILE).readText())
    }

    @Test
    fun `save leaves an existing file unchanged when the temp write fails after open`() {
        val pkg = "com.example.truncate"
        val canonical = appCanonical(globalRoot, pkg, 0)
        writeReadableAppFiles(canonical)
        val before = snapshotFiles(canonical, APP_CONFIG_FILE_NAMES)
        val config = AppConfig(pkg, 0)
        config.shotConfig.enabled = true
        ConfigFileStore.afterTempOpened = { throw IOException("fail after open") }

        assertThrows(IOException::class.java) { config.save() }

        assertSnapshot(canonical, before)
        assertNoTempFiles(canonical)
    }

    @Test
    fun `save leaves files unchanged when an existing config cannot be parsed`() {
        val pkg = "com.example.parse"
        val canonical = appCanonical(globalRoot, pkg, 0)
        writeReadableAppFiles(canonical)
        File(canonical, ConfigFiles.SHOT_CONFIG_FILE).writeText("{")
        val before = snapshotFiles(canonical, APP_CONFIG_FILE_NAMES)

        val config = AppConfig(pkg, 0)
        assertThrows(IllegalStateException::class.java) { config.save() }

        assertSnapshot(canonical, before)
        assertNoTempFiles(canonical)
    }

    @Test
    fun `save leaves an existing file unchanged when replace fails`() {
        val pkg = "com.example.replace"
        val canonical = appCanonical(globalRoot, pkg, 0)
        writeReadableAppFiles(canonical)
        val before = snapshotFiles(canonical, APP_CONFIG_FILE_NAMES)
        val config = AppConfig(pkg, 0)
        config.shotConfig.enabled = true
        ConfigFileStore.replaceTemp = { _, _ -> throw IOException("replace failed") }

        assertThrows(IOException::class.java) { config.save() }

        assertSnapshot(canonical, before)
        assertNoTempFiles(canonical)
    }

    @Test
    fun `missing files load as defaults and save creates them`() {
        val pkg = "com.example.missing"
        val canonical = appCanonical(globalRoot, pkg, 0)
        val config = AppConfig(pkg, 0)
        assertFalse(File(canonical, ConfigFiles.SHOT_CONFIG_FILE).exists())
        assertFalse(config.shotConfig.enabled)
        assertTrue(config.shotConfig.permission)
        assertEquals(CompressItems.all, config.shotConfig.items)

        config.shotConfig.enabled = true
        config.save()

        val items = File(canonical, ConfigFiles.SHOT_CONFIG_FILE)
        assertTrue(items.isFile)
        val saved = ShotConfig.fromJson(items.readText())
        assertTrue(saved.enabled)
        assertEquals(CompressItems.all, saved.items)
    }

    @Test
    fun `non zero user does not read or migrate the legacy package directory`() {
        val pkg = "com.example.user10"
        val legacyItems = """{"enabled":true,"permission":false,"items":["data"]}"""
        val legacy = appLegacy(globalRoot, pkg)
        legacy.mkdirs()
        File(legacy, ConfigFiles.SHOT_CONFIG_FILE).writeText(legacyItems)

        val config = AppConfig(pkg, 10)
        assertFalse(config.shotConfig.enabled)
        assertTrue(config.shotConfig.permission)
        assertEquals(CompressItems.all, config.shotConfig.items)
        config.shotConfig.enabled = true
        config.save()

        val canonical = appCanonical(globalRoot, pkg, 10)
        assertTrue(File(canonical, ConfigFiles.SHOT_CONFIG_FILE).isFile)
        val saved = ShotConfig.fromJson(File(canonical, ConfigFiles.SHOT_CONFIG_FILE).readText())
        assertTrue(saved.enabled)
        assertEquals(CompressItems.all, saved.items)
        assertEquals(legacyItems, File(legacy, ConfigFiles.SHOT_CONFIG_FILE).readText())
        assertTrue(legacy.isDirectory)
    }

    private fun writeReadableAppFiles(dir: File) {
        dir.mkdirs()
        File(dir, ConfigFiles.SHOT_CONFIG_FILE).writeText(
            """{"enabled":false,"permission":true,"items":["data"]}"""
        )
        File(dir, ConfigFiles.EXCLUDE_CONFIG_FILE).writeText("""{"data":["cache/**"]}""")
        File(dir, ConfigFiles.ACTION_CONFIG_FILE).writeText(
            """{"enabled":true,"compressLevel":12,"uninstallArchived":true}"""
        )
        File(dir, ConfigFiles.EXTRA_CONFIG_FILE).writeText("""{"items":[]}""")
    }
}

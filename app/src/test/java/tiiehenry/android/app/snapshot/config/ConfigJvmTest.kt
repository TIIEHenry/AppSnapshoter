package tiiehenry.android.app.snapshot.config

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

internal val GROUP_CONFIG_FILE_NAMES = listOf(
    ConfigFiles.SHOT_CONFIG_FILE,
    ConfigFiles.EXCLUDE_CONFIG_FILE,
    ConfigFiles.ACTION_CONFIG_FILE,
    ConfigFiles.GROUP_CONFIG_FILE,
)

internal val APP_CONFIG_FILE_NAMES = listOf(
    ConfigFiles.SHOT_CONFIG_FILE,
    ConfigFiles.EXCLUDE_CONFIG_FILE,
    ConfigFiles.ACTION_CONFIG_FILE,
    ConfigFiles.EXTRA_CONFIG_FILE,
)

open class ConfigJvmTest {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    protected lateinit var globalRoot: File

    @Before
    fun setUpConfigEnvironment() {
        globalRoot = temporaryFolder.newFolder("global-root")
        ConfigEnvironment.setGlobalRootPathForTest { globalRoot.absolutePath }
        ConfigEnvironment.setAccentColorForTest(
            parse = { hex ->
                if (hex.isNullOrBlank()) {
                    null
                } else {
                    hex.trim().removePrefix("#").toLong(16).toInt()
                }
            },
            defaultColor = { 0xFF0078D4.toInt() },
            format = { color -> String.format("#%08X", color) },
        )
        ConfigFileStore.resetHooks()
        AppConfigManager.getInstance().clearAllConfigs()
    }

    @After
    fun tearDownConfigEnvironment() {
        ConfigFileStore.resetHooks()
        ConfigEnvironment.resetForTest()
        AppConfigManager.getInstance().clearAllConfigs()
    }

    protected fun appCanonical(root: File, packageName: String, userId: Int): File =
        File(root, "app_configs/${AppConfigManager.configKey(packageName, userId)}")

    protected fun appLegacy(root: File, packageName: String): File =
        File(root, "app_configs/$packageName")

    protected fun snapshotFiles(dir: File, names: List<String>): Map<String, ByteArray?> =
        names.associateWith { name ->
            val file = File(dir, name)
            if (file.isFile) file.readBytes() else null
        }

    protected fun assertSnapshot(dir: File, before: Map<String, ByteArray?>) {
        before.forEach { (name, bytes) ->
            val file = File(dir, name)
            if (bytes == null) {
                assertFalse(file.exists())
            } else {
                assertTrue(file.isFile)
                assertArrayEquals(bytes, file.readBytes())
            }
        }
    }

    protected fun assertNoTempFiles(dir: File) {
        if (!dir.exists()) return
        val temps = dir.walkTopDown().filter { it.isFile && it.name.endsWith(".tmp") }.toList()
        assertTrue(temps.toString(), temps.isEmpty())
    }
}

internal class MemoryConfigPrefs : ConfigPrefs {
    private val strings = HashMap<String, String>()
    private val bools = HashMap<String, Boolean>()
    private val ints = HashMap<String, Int>()

    override fun decodeString(key: String): String? = if (strings.containsKey(key)) strings[key] else null

    override fun encode(key: String, value: String) {
        strings[key] = value
    }

    override fun containsKey(key: String): Boolean =
        strings.containsKey(key) || bools.containsKey(key) || ints.containsKey(key)

    override fun decodeBool(key: String, defaultValue: Boolean): Boolean = bools[key] ?: defaultValue

    override fun encode(key: String, value: Boolean) {
        bools[key] = value
    }

    override fun decodeInt(key: String, defaultValue: Int): Int = ints[key] ?: defaultValue

    override fun encode(key: String, value: Int) {
        ints[key] = value
    }

    override fun removeValueForKey(key: String) {
        strings.remove(key)
        bools.remove(key)
        ints.remove(key)
    }

    override fun clearAll() {
        strings.clear()
        bools.clear()
        ints.clear()
    }
}

internal fun writeGroupFiles(
    dir: File,
    items: String,
    excludes: String,
    action: String,
    group: String,
) {
    dir.mkdirs()
    File(dir, ConfigFiles.SHOT_CONFIG_FILE).writeText(items)
    File(dir, ConfigFiles.EXCLUDE_CONFIG_FILE).writeText(excludes)
    File(dir, ConfigFiles.ACTION_CONFIG_FILE).writeText(action)
    File(dir, ConfigFiles.GROUP_CONFIG_FILE).writeText(group)
}

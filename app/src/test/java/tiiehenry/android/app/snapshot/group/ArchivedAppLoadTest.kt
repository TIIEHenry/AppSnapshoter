package tiiehenry.android.app.snapshot.group

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tiiehenry.android.app.snapshot.archive.MetaInfoHelper
import tiiehenry.android.app.snapshot.archive.bean.MetaDataItem
import tiiehenry.android.app.snapshot.main.launch.batch.BatchTestStubAppManager
import tiiehenry.android.snapshot.fs.IFileType

class ArchivedAppLoadTest {

    @Before
    fun setUp() {
        ArchivedApp.resetArchiveReaders()
    }

    @After
    fun tearDown() {
        ArchivedApp.resetArchiveReaders()
    }

    @Test
    fun secondArchiveFileTypeFailureKeepsFirstAndThird() {
        val packageDir = "/snap/g/com.example"
        val app = archivedApp(packageDir)
        val fs = archiveFs(packageDir, listOf("a", "b", "c")) { path ->
            if (path == childPath(packageDir, "b")) throw IllegalStateException("fileType")
            if (path.endsWith("/${MetaInfoHelper.META_INFO_FILE_NAME}")) {
                IFileType.TYPE_FILE
            } else {
                IFileType.TYPE_DIR
            }
        }
        ArchivedApp.metaReader = { _, _ -> testMeta("com.example") }
        app.loadArchives(fs, BatchTestStubAppManager, false)
        assertEquals(listOf("a", "c"), app.archives.keys.toList())
        assertFalse(app.archivesComplete)
    }

    @Test
    fun secondArchiveReadDataItemsFailureKeepsFirstAndThird() {
        val packageDir = "/snap/g/com.example"
        val app = archivedApp(packageDir)
        val fs = archiveFs(packageDir, listOf("a", "b", "c")) { path ->
            if (path.endsWith("/${MetaInfoHelper.META_INFO_FILE_NAME}")) {
                IFileType.TYPE_FILE
            } else {
                IFileType.TYPE_DIR
            }
        }
        ArchivedApp.metaReader = { _, _ -> testMeta("com.example", dataItems = listOf("data.json")) }
        ArchivedApp.dataItemsReader = { _, dir ->
            if (dir == childPath(packageDir, "b")) throw IllegalStateException("items")
            emptyList()
        }
        app.loadArchives(fs, BatchTestStubAppManager, false)
        assertEquals(listOf("a", "c"), app.archives.keys.toList())
        assertFalse(app.archives.containsKey("b"))
        assertFalse(app.archivesComplete)
    }

    @Test
    fun extraItemFailureKeepsTheOtherKeys() {
        val packageDir = "/snap/g/com.example"
        val app = archivedApp(packageDir)
        val fs = archiveFs(packageDir, listOf("a")) { path ->
            if (path.endsWith("/${MetaInfoHelper.META_INFO_FILE_NAME}")) {
                IFileType.TYPE_FILE
            } else {
                IFileType.TYPE_DIR
            }
        }
        ArchivedApp.metaReader = { _, _ ->
            testMeta(
                "com.example",
                extraItems = linkedMapOf("ok.json" to "kept", "bad.json" to "drop"),
            )
        }
        ArchivedApp.extraItemReader = { name, _ ->
            if (name == "bad.json") throw IllegalStateException("bad")
            MetaDataItem().apply { this.name = name }
        }
        app.loadArchives(fs, BatchTestStubAppManager, false)
        val extra = app.archives.getValue("a").extraItems
        assertEquals(setOf("kept"), extra.values.toSet())
        assertEquals("ok.json", extra.keys.single().name)
        assertTrue(app.archivesComplete)
    }

    @Test
    fun incompleteArchivesAreRereadWithoutDuplicatingThePackage() {
        val groupPath = "/snap/g"
        val packageDir = childPath(groupPath, "com.example")
        val group = SnapGroup("g").detach(groupPath)
        var groupLists = 0
        var packageLists = 0
        var failSecond = true
        val fs = ScriptedFileSystem()
        fs.onListDir = { path ->
            when (path) {
                groupPath -> {
                    groupLists++
                    mutableListOf("com.example")
                }
                packageDir -> {
                    packageLists++
                    mutableListOf("a", "b", "c")
                }
                else -> mutableListOf()
            }
        }
        fs.onFileType = { path ->
            if (path.endsWith("/${MetaInfoHelper.META_INFO_FILE_NAME}")) {
                IFileType.TYPE_FILE
            } else {
                IFileType.TYPE_DIR
            }
        }
        ArchivedApp.metaReader = { _, _ -> testMeta("com.example", dataItems = listOf("data.json")) }
        ArchivedApp.dataItemsReader = { _, dir ->
            if (failSecond && dir == childPath(packageDir, "b")) {
                throw IllegalStateException("items")
            }
            emptyList()
        }
        val first = group.loadApps(unusedContext, fs, BatchTestStubAppManager, true)
        val app = first.single()
        assertEquals(setOf("a", "c"), app.archives.keys)
        assertFalse(app.archivesComplete)
        assertEquals(1, groupLists)
        assertEquals(1, packageLists)

        failSecond = false
        val second = group.loadApps(unusedContext, fs, BatchTestStubAppManager, false)
        assertSame(app, second.single())
        assertEquals(setOf("a", "b", "c"), app.archives.keys)
        assertTrue(app.archivesComplete)
        assertEquals(1, group.apps.size)
        assertEquals(1, groupLists)
        assertEquals(2, packageLists)

        val third = group.loadApps(unusedContext, fs, BatchTestStubAppManager, false)
        assertSame(app, third.single())
        assertEquals(1, groupLists)
        assertEquals(2, packageLists)
    }

    @Test
    fun packageListingFailureStillAddsIncompleteAppAndReloadFillsIt() {
        val groupPath = "/snap/g"
        val packageDir = childPath(groupPath, "com.example")
        val group = SnapGroup("g").detach(groupPath)
        var packageLists = 0
        val fs = ScriptedFileSystem()
        fs.onListDir = { path ->
            when (path) {
                groupPath -> mutableListOf("com.example")
                packageDir -> {
                    packageLists++
                    if (packageLists == 1) throw IllegalStateException("list")
                    mutableListOf("a")
                }
                else -> mutableListOf()
            }
        }
        fs.onFileType = { path ->
            if (path.endsWith("/${MetaInfoHelper.META_INFO_FILE_NAME}")) {
                IFileType.TYPE_FILE
            } else {
                IFileType.TYPE_DIR
            }
        }
        ArchivedApp.metaReader = { _, _ -> testMeta("com.example") }
        val first = group.loadApps(unusedContext, fs, BatchTestStubAppManager, true)
        val app = first.single()
        assertTrue(app.archives.isEmpty())
        assertFalse(app.archivesComplete)
        val second = group.loadApps(unusedContext, fs, BatchTestStubAppManager, false)
        assertSame(app, second.single())
        assertEquals(setOf("a"), app.archives.keys)
        assertTrue(app.archivesComplete)
        assertEquals(2, packageLists)
    }

    private fun archivedApp(packageDir: String): ArchivedApp {
        val group = SnapGroup("g").detach("/snap/g")
        return ArchivedApp(group, packageDir, "$packageDir.png")
    }

    private fun archiveFs(
        packageDir: String,
        names: List<String>,
        fileType: (String) -> Int,
    ): ScriptedFileSystem {
        val fs = ScriptedFileSystem()
        fs.onListDir = { path ->
            if (path == packageDir) names.toMutableList() else mutableListOf()
        }
        fs.onFileType = fileType
        return fs
    }
}

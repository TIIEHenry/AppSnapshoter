package tiiehenry.android.app.snapshot.main.launch.batch

import org.junit.Assert.*
import org.junit.Test
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.archive.bean.MetaInfo
import tiiehenry.android.app.snapshot.archive.bean.MetaPackageInfo
import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.app.snapshot.group.SnapGroup

class GroupBatchRestorePlannerTest {

    private fun createApp(group: SnapGroup, packageName: String, userId: Int = 0): ArchivedApp {
        val app = ArchivedApp(group, "/data/$packageName", "/icons/$packageName.png")
        app.appInfo = AppInfo(BatchTestStubFileSystem, BatchTestStubAppManager, packageName, userId)
        group.apps.add(app)
        return app
    }

    private fun addArchive(app: ArchivedApp, name: String, makeTime: Long): ArchiveItem {
        val pkgInfo = MetaPackageInfo("Test", app.appInfo.packageName, 1L, "1.0", 0L, 0, 0L, 0L)
        val metaInfo = MetaInfo(pkgInfo, app.appInfo.userId, "", emptyList(), null, emptyList(), makeTime, false)
        val item = ArchiveItem(metaInfo, app.appInfo, name, "/data/${app.appInfo.packageName}/$name", emptyList())
        app.archives[name] = item
        return item
    }

    @Test
    fun `ALL scope with NEWEST picks latest archive`() {
        val group = SnapGroup("g1")
        val app = createApp(group, "com.a")
        addArchive(app, "old", 1000L)
        addArchive(app, "new", 5000L)

        val preview = GroupBatchRestorePlanner.preview(
            group, GroupRestoreScope.ALL, ArchivePickStrategy.NEWEST, emptyMap(), { false }
        )
        assertEquals(1, preview.tasks.size)
        assertEquals("new", preview.tasks[0].archive.name)
    }

    @Test
    fun `NOT_INSTALLED scope filters installed apps`() {
        val group = SnapGroup("g1")
        val installed = createApp(group, "com.installed")
        addArchive(installed, "a1", 1000L)
        val notInstalled = createApp(group, "com.missing")
        addArchive(notInstalled, "a1", 1000L)

        val preview = GroupBatchRestorePlanner.preview(
            group,
            GroupRestoreScope.NOT_INSTALLED,
            ArchivePickStrategy.NEWEST,
            emptyMap(),
            { app -> app.appInfo.packageName == "com.installed" }
        )
        assertEquals(1, preview.tasks.size)
        assertEquals("com.missing", preview.tasks[0].app.appInfo.packageName)
    }

    @Test
    fun `SINCE_LAST_RESTORE without record includes app`() {
        val group = SnapGroup("g1")
        val app = createApp(group, "com.a")
        addArchive(app, "a1", 1000L)

        val preview = GroupBatchRestorePlanner.preview(
            group, GroupRestoreScope.SINCE_LAST_RESTORE, ArchivePickStrategy.NEWEST, emptyMap(), { false }
        )
        assertEquals(1, preview.tasks.size)
    }

    @Test
    fun `SINCE_LAST_RESTORE with newer archive includes app`() {
        val group = SnapGroup("g1")
        val app = createApp(group, "com.a")
        addArchive(app, "old", 1000L)
        addArchive(app, "new", 5000L)
        val records = mapOf("com.a:0" to RestoreRecord(0L, "old", 1000L))

        val preview = GroupBatchRestorePlanner.preview(
            group, GroupRestoreScope.SINCE_LAST_RESTORE, ArchivePickStrategy.NEWEST, records, { false }
        )
        assertEquals(1, preview.tasks.size)
    }

    @Test
    fun `SINCE_LAST_RESTORE with no newer archive excludes app`() {
        val group = SnapGroup("g1")
        val app = createApp(group, "com.a")
        addArchive(app, "only", 1000L)
        val records = mapOf("com.a:0" to RestoreRecord(0L, "only", 1000L))

        val preview = GroupBatchRestorePlanner.preview(
            group, GroupRestoreScope.SINCE_LAST_RESTORE, ArchivePickStrategy.NEWEST, records, { false }
        )
        assertTrue(preview.tasks.isEmpty())
    }

    @Test
    fun `LAST_RESTORED with deleted archive sets fallbackCount`() {
        val group = SnapGroup("g1")
        val app = createApp(group, "com.a")
        addArchive(app, "new", 5000L)
        val records = mapOf("com.a:0" to RestoreRecord(0L, "gone", 2000L))

        val preview = GroupBatchRestorePlanner.preview(
            group, GroupRestoreScope.ALL, ArchivePickStrategy.LAST_RESTORED, records, { false }
        )
        assertEquals(1, preview.tasks.size)
        assertEquals(1, preview.fallbackCount)
        assertTrue(preview.tasks[0].fallbackToNewest)
    }

    @Test
    fun `resolveTaskAt returns refreshed app and archive`() {
        val group = SnapGroup("g1")
        val app = createApp(group, "com.a")
        val archive = addArchive(app, "a1", 1000L)
        val task = GroupRestoreTask(app, archive)

        val resolved = GroupBatchRestorePlanner.resolveTaskAt(task, listOf(group))
        assertNotNull(resolved)
        assertEquals("a1", resolved!!.archive.name)
    }

    @Test
    fun `resolveTaskAt returns null when archive removed`() {
        val group = SnapGroup("g1")
        val app = createApp(group, "com.a")
        val archive = addArchive(app, "a1", 1000L)
        val task = GroupRestoreTask(app, archive)
        app.archives.clear()

        assertNull(GroupBatchRestorePlanner.resolveTaskAt(task, listOf(group)))
    }
}

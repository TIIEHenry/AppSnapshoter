package tiiehenry.android.app.snapshot.main.timeline

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.os.UserHandle
import org.junit.Assert.*
import org.junit.Test
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.archive.bean.MetaInfo
import tiiehenry.android.app.snapshot.archive.bean.MetaPackageInfo
import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.snapshot.app.AppPermission
import tiiehenry.android.snapshot.app.IAppManager
import tiiehenry.android.snapshot.app.UserInfoHide
import tiiehenry.android.snapshot.file.IFileCompressor
import tiiehenry.android.snapshot.file.IFileSystem

class TimelineRepositoryTest {

    private fun createApp(
        group: SnapGroup,
        packageName: String,
        userId: Int = 0,
        label: String = packageName
    ): ArchivedApp {
        val app = ArchivedApp(group, "/data/$packageName", "/icons/$packageName.png")
        val appInfo = AppInfo(StubFileSystem, StubAppManager, packageName, userId)
        appInfo.archiveLabel = label
        app.appInfo = appInfo
        group.apps.add(app)
        return app
    }

    private fun addArchive(
        app: ArchivedApp,
        name: String,
        makeTime: Long
    ): ArchiveItem {
        val pkgInfo = MetaPackageInfo(app.appInfo.label, app.appInfo.packageName, 1L, "1.0", 0L, 0, 0L, 0L)
        val metaInfo = MetaInfo(pkgInfo, app.appInfo.userId, "", emptyList(), null, emptyList(), makeTime, false)
        val item = ArchiveItem(metaInfo, app.appInfo, name, "/data/${app.appInfo.packageName}/$name", emptyList())
        app.archives[name] = item
        return item
    }

    @Test
    fun `empty group list returns empty results`() {
        val result = TimelineRepository.query(emptyList(), TimelineRepository.defaultLast7Days())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `no matching archives in time range returns empty`() {
        val group = SnapGroup("g1")
        val app = createApp(group, "com.test.app")
        addArchive(app, "archive1", 1000L)

        val range = TimeRange(5000L, 10000L)
        val result = TimelineRepository.query(listOf(group), range)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `matching archive returns entry with correct fields`() {
        val group = SnapGroup("g1")
        val app = createApp(group, "com.test.app", label = "Test App")
        addArchive(app, "archive1", 8000L)

        val range = TimeRange(5000L, 10000L)
        val result = TimelineRepository.query(listOf(group), range)

        assertEquals(1, result.size)
        val entry = result[0]
        assertEquals("g1", entry.key.groupId)
        assertEquals("com.test.app", entry.key.packageName)
        assertEquals(0, entry.key.userId)
        assertEquals("Test App", entry.appLabel)
        assertEquals(listOf("archive1"), entry.matchingArchiveNames)
        assertEquals(listOf(8000L), entry.matchingArchiveTimes)
    }

    @Test
    fun `left-closed right-open boundary`() {
        val group = SnapGroup("g1")
        val app = createApp(group, "com.test.app")
        addArchive(app, "at-start", 5000L)
        addArchive(app, "before-end", 9999L)
        addArchive(app, "at-end", 10000L)
        addArchive(app, "before-start", 4999L)

        val range = TimeRange(5000L, 10000L)
        val result = TimelineRepository.query(listOf(group), range)

        assertEquals(1, result.size)
        val entry = result[0]
        assertEquals(3, entry.matchingArchiveNames.size)
        assertTrue("at-start" in entry.matchingArchiveNames)
        assertTrue("before-end" in entry.matchingArchiveNames)
        assertTrue("at-end" in entry.matchingArchiveNames)
        assertFalse("before-start" in entry.matchingArchiveNames)
    }

    @Test
    fun `multiple archives sorted by makeTime descending`() {
        val group = SnapGroup("g1")
        val app = createApp(group, "com.test.app")
        addArchive(app, "old", 6000L)
        addArchive(app, "new", 9000L)
        addArchive(app, "mid", 7500L)

        val range = TimeRange(5000L, 10000L)
        val result = TimelineRepository.query(listOf(group), range)

        assertEquals(1, result.size)
        val entry = result[0]
        assertEquals(listOf("new", "mid", "old"), entry.matchingArchiveNames)
        assertEquals(listOf(9000L, 7500L, 6000L), entry.matchingArchiveTimes)
    }

    @Test
    fun `entries sorted by latest snapshot descending`() {
        val group = SnapGroup("g1")
        val app1 = createApp(group, "com.old.app")
        addArchive(app1, "a1", 6000L)
        val app2 = createApp(group, "com.new.app")
        addArchive(app2, "a1", 9000L)

        val range = TimeRange(5000L, 10000L)
        val result = TimelineRepository.query(listOf(group), range)

        assertEquals(2, result.size)
        assertEquals("com.new.app", result[0].key.packageName)
        assertEquals("com.old.app", result[1].key.packageName)
    }

    @Test
    fun `cross-group same package name produces separate entries`() {
        val group1 = SnapGroup("g1")
        val group2 = SnapGroup("g2")
        val app1 = createApp(group1, "com.test.app")
        val app2 = createApp(group2, "com.test.app")
        addArchive(app1, "a1", 8000L)
        addArchive(app2, "a1", 7000L)

        val range = TimeRange(5000L, 10000L)
        val result = TimelineRepository.query(listOf(group1, group2), range)

        assertEquals(2, result.size)
        val ids = result.map { it.key.id }.toSet()
        assertTrue("g1:com.test.app:0" in ids)
        assertTrue("g2:com.test.app:0" in ids)
    }

    @Test
    fun `userId distinguishes entries`() {
        val group = SnapGroup("g1")
        val app0 = createApp(group, "com.test.app", userId = 0)
        val app10 = createApp(group, "com.test.app", userId = 10)
        addArchive(app0, "a1", 8000L)
        addArchive(app10, "a1", 8000L)

        val range = TimeRange(5000L, 10000L)
        val result = TimelineRepository.query(listOf(group), range)

        assertEquals(2, result.size)
        val userIds = result.map { it.key.userId }.toSet()
        assertTrue(0 in userIds)
        assertTrue(10 in userIds)
    }

    @Test
    fun `resolveArchive selects newest with NEWEST_FIRST strategy`() {
        val archives = listOf(
            createArchiveItemWithTime(6000L),
            createArchiveItemWithTime(9000L),
            createArchiveItemWithTime(7500L)
        )
        val result = TimelineRepository.resolveArchive(archives, RestoreStrategy.NEWEST_FIRST)
        assertEquals(9000L, result.metaInfo.makeTime)
    }

    @Test
    fun `resolveArchive selects oldest with OLDEST_FIRST strategy`() {
        val archives = listOf(
            createArchiveItemWithTime(6000L),
            createArchiveItemWithTime(9000L),
            createArchiveItemWithTime(7500L)
        )
        val result = TimelineRepository.resolveArchive(archives, RestoreStrategy.OLDEST_FIRST)
        assertEquals(6000L, result.metaInfo.makeTime)
    }

    private fun createArchiveItemWithTime(makeTime: Long): ArchiveItem {
        val pkgInfo = MetaPackageInfo("Test", "com.test", 1L, "1.0", 0L, 0, 0L, 0L)
        val metaInfo = MetaInfo(pkgInfo, 0, "", emptyList(), null, emptyList(), makeTime, false)
        val appInfo = AppInfo(StubFileSystem, StubAppManager, "com.test")
        return ArchiveItem(metaInfo, appInfo, "archive_$makeTime", "/path/$makeTime", emptyList())
    }
}

private object StubFileSystem : IFileSystem {
    override fun fileType(path: String) = 0
    override fun listDir(path: String) = mutableListOf<String>()
    override fun calculateSize(path: String) = 0L
    override fun mkdirs(path: String) = true
    override fun delete(path: String) = true
    override fun exists(path: String) = false
    override fun getParent(path: String) = ""
    override fun length(path: String) = 0L
    override fun getLastModifiedTime(path: String) = 0L
    override fun setLastModifiedTime(path: String, time: Long) = true
    override fun md5(file: String) = ""
    override fun getUid(path: String) = 0
    override fun setUid(path: String, uid: Int) = true
    override fun getGid(path: String) = 0
    override fun setGid(path: String, gid: Int) = true
    override fun openFile(path: String, mode: Int) = null
    override fun openInputStream(path: String) = null
    override fun openOutputStream(path: String) = null
    override fun createTempFile(prefix: String, suffix: String) = ""
    override fun createTarArchive(sourceDir: String, targetFile: String, excludes: MutableList<String>, excludeFiles: MutableList<String>, stdErr: String, stdOut: String) {}
    override fun createTarArchiveForMultiple(files: MutableList<String>, targetFile: String, stdErr: String, stdOut: String) {}
    override fun getCompressor(): IFileCompressor? = null
    override fun mkfifo(path: String, mode: Int) = false
    override fun isFifo(path: String) = false
    override fun extractTar(tarFifo: String, targetDir: String) = false
    override fun cleanDir(path: String) = false
    override fun move(sourcePath: String, targetPath: String) = false
    override fun copyRecursively(source: String, target: String, overwrite: Boolean) = false
}

private object StubAppManager : IAppManager {
    // IPackageManager
    override fun getUsers(): MutableList<UserInfoHide> = mutableListOf()
    override fun getInstalledPackages(flags: Int, userId: Int): MutableList<String> = mutableListOf()
    override fun getPackageInfo(packageName: String, flags: Int, userId: Int): PackageInfo? = null
    override fun getApplicationInfo(packageName: String, flags: Int, userId: Int): ApplicationInfo? = null
    override fun loadLabel(packageName: String, userId: Int): String = packageName
    override fun loadIcon(packageName: String, userId: Int): Bitmap? = null
    override fun getDir(packageName: String, userId: Int, type: Int): String = ""
    override fun isInstalled(packageName: String, userId: Int) = false
    override fun installApk(file: String, userId: Int) = false
    override fun installApks(files: MutableList<String>, userId: Int) = false
    override fun uninstallApk(packageName: String, userId: Int) = false
    override fun forceStopPackage(packageName: String, userId: Int) = true
    override fun clearAppData(packageName: String, userId: Int) {}
    override fun suspendPackage(packageName: String, userId: Int) = true
    override fun unsuspendPackage(packageName: String, userId: Int) = true
    override fun isPackageRunning(packageName: String, userId: Int) = false
    override fun launchApp(packageName: String, userId: Int) = false
    // IPermissionManager
    override fun getPermissions(packageName: String, userId: Int): MutableList<AppPermission> = mutableListOf()
    override fun setAppPermission(packageName: String, userId: Int, permission: AppPermission) {}
    override fun setAppPermissions(packageName: String, userId: Int, permissions: MutableList<AppPermission>) {}
    override fun grantRuntimePermission(packageName: String, permName: String, user: UserHandle) {}
    override fun revokeRuntimePermission(packageName: String, permName: String, user: UserHandle) {}
    override fun getPermissionFlags(packageName: String, permName: String, user: UserHandle) = 0
    override fun updatePermissionFlags(packageName: String, permName: String, user: UserHandle, flagMask: Int, flagValues: Int) {}
    override fun getPackageUid(packageName: String, userId: Int) = 0
    override fun getUserHandle(userId: Int): UserHandle? = null
    override fun setOpsMode(code: Int, uid: Int, packageName: String, mode: Int) {}
    override fun resetAppOps(userId: Int, packageName: String) {}
    override fun getPackageSsaidAsUser(packageName: String, uid: Int, userId: Int) = ""
    override fun setPackageSsaidAsUser(packageName: String, userId: Int, ssaid: String) {}
}

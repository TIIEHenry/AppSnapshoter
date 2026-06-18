package tiiehenry.android.app.snapshot.main.launch.batch

import org.junit.Assert.*
import org.junit.Test
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.archive.bean.MetaInfo
import tiiehenry.android.app.snapshot.archive.bean.MetaPackageInfo
import tiiehenry.android.app.snapshot.main.timeline.RestoreStrategy

class ArchiveResolverTest {

    private fun createArchiveItem(makeTime: Long, name: String = "archive_$makeTime"): ArchiveItem {
        val pkgInfo = MetaPackageInfo("Test", "com.test", 1L, "1.0", 0L, 0, 0L, 0L)
        val metaInfo = MetaInfo(pkgInfo, 0, "", emptyList(), null, emptyList(), makeTime, false)
        val appInfo = AppInfo(BatchTestStubFileSystem, BatchTestStubAppManager, "com.test")
        return ArchiveItem(metaInfo, appInfo, name, "/path/$name", emptyList())
    }

    @Test
    fun `pick newest with RestoreStrategy`() {
        val archives = listOf(
            createArchiveItem(6000L),
            createArchiveItem(9000L),
            createArchiveItem(7500L)
        )
        val result = ArchiveResolver.pick(archives, RestoreStrategy.NEWEST_FIRST)
        assertEquals(9000L, result.metaInfo.makeTime)
    }

    @Test
    fun `pick oldest with RestoreStrategy`() {
        val archives = listOf(
            createArchiveItem(6000L),
            createArchiveItem(9000L),
            createArchiveItem(7500L)
        )
        val result = ArchiveResolver.pick(archives, RestoreStrategy.OLDEST_FIRST)
        assertEquals(6000L, result.metaInfo.makeTime)
    }

    @Test
    fun `pick NEWEST ArchivePickStrategy`() {
        val archives = listOf(createArchiveItem(1000L), createArchiveItem(3000L))
        val result = ArchiveResolver.pick(archives, ArchivePickStrategy.NEWEST, null)
        assertEquals(3000L, result.archive.metaInfo.makeTime)
        assertFalse(result.fallbackToNewest)
    }

    @Test
    fun `pick OLDEST ArchivePickStrategy`() {
        val archives = listOf(createArchiveItem(1000L), createArchiveItem(3000L))
        val result = ArchiveResolver.pick(archives, ArchivePickStrategy.OLDEST, null)
        assertEquals(1000L, result.archive.metaInfo.makeTime)
    }

    @Test
    fun `LAST_RESTORED with matching record`() {
        val archives = listOf(
            createArchiveItem(1000L, "old"),
            createArchiveItem(3000L, "new")
        )
        val record = RestoreRecord(0L, "old", 1000L)
        val result = ArchiveResolver.pick(archives, ArchivePickStrategy.LAST_RESTORED, record)
        assertEquals("old", result.archive.name)
        assertFalse(result.fallbackToNewest)
    }

    @Test
    fun `LAST_RESTORED without record falls back to newest`() {
        val archives = listOf(createArchiveItem(1000L), createArchiveItem(3000L))
        val result = ArchiveResolver.pick(archives, ArchivePickStrategy.LAST_RESTORED, null)
        assertEquals(3000L, result.archive.metaInfo.makeTime)
        assertTrue(result.fallbackToNewest)
    }

    @Test
    fun `LAST_RESTORED with deleted archive falls back to newest`() {
        val archives = listOf(createArchiveItem(1000L), createArchiveItem(3000L))
        val record = RestoreRecord(0L, "missing", 2000L)
        val result = ArchiveResolver.pick(archives, ArchivePickStrategy.LAST_RESTORED, record)
        assertEquals(3000L, result.archive.metaInfo.makeTime)
        assertTrue(result.fallbackToNewest)
    }
}

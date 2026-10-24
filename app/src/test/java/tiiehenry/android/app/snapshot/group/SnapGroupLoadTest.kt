package tiiehenry.android.app.snapshot.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.main.launch.batch.BatchTestStubAppManager
import tiiehenry.android.app.snapshot.main.launch.batch.BatchTestStubFileSystem
import tiiehenry.android.snapshot.fs.IFileType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class SnapGroupLoadTest {

    @Test(timeout = 8000)
    fun concurrentReloadDropsPackagesFilledBeforeReplace() {
        val groupPath = "/snap/g"
        val group = SnapGroup("g").detach(groupPath)
        val stale = ArchivedApp(group, "$groupPath/com.old", "$groupPath/com.old.png")
        stale.archivesComplete = true
        group.apps.add(stale)
        val intruder = ArchivedApp(group, "$groupPath/com.example", "$groupPath/com.example.png")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val groupLists = AtomicInteger()
        val fs = ScriptedFileSystem()
        fs.onListDir = { path ->
            if (path == groupPath) {
                if (groupLists.incrementAndGet() == 1) {
                    group.apps.add(intruder)
                    entered.countDown()
                    if (!release.await(3, TimeUnit.SECONDS)) {
                        throw IllegalStateException("reload listDir was not released")
                    }
                }
                mutableListOf("com.example")
            } else {
                mutableListOf()
            }
        }
        val reloadFailure = AtomicReference<Throwable>()
        val loadFailure = AtomicReference<Throwable>()
        val reloaded = AtomicReference<MutableList<ArchivedApp>>()
        val loaded = AtomicReference<MutableList<ArchivedApp>>()
        val loader = thread(start = true, isDaemon = true, name = "reload") {
            try {
                reloaded.set(group.loadApps(unusedContext, fs, BatchTestStubAppManager, true))
            } catch (t: Throwable) {
                reloadFailure.set(t)
            }
        }
        var follower: Thread? = null
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            follower = thread(start = true, isDaemon = true, name = "load") {
                try {
                    loaded.set(group.loadApps(unusedContext, fs, BatchTestStubAppManager, false))
                } catch (t: Throwable) {
                    loadFailure.set(t)
                }
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (System.nanoTime() < deadline &&
                follower.isAlive &&
                follower.state != Thread.State.BLOCKED
            ) {
                Thread.yield()
            }
            release.countDown()
            loader.join(3000)
            follower.join(3000)
            assertTrue(!loader.isAlive)
            assertTrue(!follower.isAlive)
        } finally {
            release.countDown()
            loader.join(3000)
            follower?.join(3000)
        }
        assertNull(reloadFailure.get()?.stackTraceToString(), reloadFailure.get())
        assertNull(loadFailure.get()?.stackTraceToString(), loadFailure.get())
        assertEquals(1, groupLists.get())
        assertEquals(listOf("com.example"), packageNames(group))
        assertSame(reloaded.get(), loaded.get())
        assertSame(group.apps.single(), reloaded.get()!!.single())
    }

    @Test
    fun listDirFailureLeavesOriginalAppsAndReloadFalseDoesNotReturnPartial() {
        val groupPath = "/snap/g"
        val group = SnapGroup("g").detach(groupPath)
        val kept = keptApp(group, "com.kept")
        var groupLists = 0
        val fs = ScriptedFileSystem()
        fs.onListDir = { path ->
            if (path == groupPath) {
                groupLists++
                throw IllegalStateException("listDir")
            }
            mutableListOf()
        }
        assertThrows(IllegalStateException::class.java) {
            group.loadApps(unusedContext, fs, BatchTestStubAppManager, true)
        }
        assertEquals(listOf(kept), group.apps.toList())
        assertSame(kept, group.apps.single())
        val listsAfterFailure = groupLists
        val again = group.loadApps(unusedContext, fs, BatchTestStubAppManager, false)
        assertSame(kept, again.single())
        assertEquals(listsAfterFailure, groupLists)
        assertEquals(listOf("com.kept"), packageNames(group))
    }

    @Test
    fun fileTypeFailureLeavesOriginalAppsAndReloadFalseDoesNotReturnPartial() {
        val groupPath = "/snap/g"
        val group = SnapGroup("g").detach(groupPath)
        val kept = keptApp(group, "com.kept")
        var groupLists = 0
        val fs = ScriptedFileSystem()
        fs.onListDir = { path ->
            if (path == groupPath) {
                groupLists++
                mutableListOf("com.fresh", "com.boom")
            } else {
                mutableListOf()
            }
        }
        fs.onFileType = { path ->
            if (path == childPath(groupPath, "com.boom")) {
                throw IllegalStateException("fileType")
            }
            IFileType.TYPE_DIR
        }
        assertThrows(IllegalStateException::class.java) {
            group.loadApps(unusedContext, fs, BatchTestStubAppManager, true)
        }
        assertEquals(1, group.apps.size)
        assertSame(kept, group.apps.single())
        val listsAfterFailure = groupLists
        val again = group.loadApps(unusedContext, fs, BatchTestStubAppManager, false)
        assertSame(kept, again.single())
        assertEquals(listsAfterFailure, groupLists)
        assertEquals(listOf("com.kept"), packageNames(group))
    }

    @Test
    fun fileTypeFailureOnEmptyGroupDoesNotCachePartialAsComplete() {
        val groupPath = "/snap/g"
        val group = SnapGroup("g").detach(groupPath)
        var groupLists = 0
        val fs = ScriptedFileSystem()
        fs.onListDir = { path ->
            if (path == groupPath) {
                groupLists++
                mutableListOf("com.fresh", "com.boom")
            } else {
                mutableListOf()
            }
        }
        fs.onFileType = { path ->
            if (path == childPath(groupPath, "com.boom")) {
                throw IllegalStateException("fileType")
            }
            IFileType.TYPE_DIR
        }
        assertThrows(IllegalStateException::class.java) {
            group.loadApps(unusedContext, fs, BatchTestStubAppManager, true)
        }
        assertTrue(group.apps.isEmpty())
        assertThrows(IllegalStateException::class.java) {
            group.loadApps(unusedContext, fs, BatchTestStubAppManager, false)
        }
        assertTrue(group.apps.isEmpty())
        assertEquals(2, groupLists)
    }

    private fun keptApp(group: SnapGroup, pkg: String): ArchivedApp {
        val app = ArchivedApp(group, "${group.detachedPath}/$pkg", "${group.detachedPath}/$pkg.png")
        app.archivesComplete = true
        app.appInfo = AppInfo(
            BatchTestStubFileSystem,
            BatchTestStubAppManager,
            pkg,
            group.detachedUserId ?: 0,
        )
        group.apps.add(app)
        return app
    }
}

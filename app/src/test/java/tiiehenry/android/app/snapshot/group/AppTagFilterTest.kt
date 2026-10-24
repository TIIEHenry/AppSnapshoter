package tiiehenry.android.app.snapshot.group

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.app.tag.AppTag
import tiiehenry.android.app.snapshot.app.tag.AppTagHelper
import tiiehenry.android.app.snapshot.main.launch.batch.BatchTestStubAppManager
import tiiehenry.android.app.snapshot.main.launch.batch.BatchTestStubFileSystem
import tiiehenry.android.snapshot.fs.IFileType

class AppTagFilterTest {

    @Before
    fun setUp() {
        AppTagHelper.clearCache()
    }

    @After
    fun tearDown() {
        AppTagHelper.resetProviders()
        AppTagHelper.clearCache()
    }

    @Test
    fun unresolvedGroupTagsYieldEmptyNotTheOriginalList() {
        val apps = listOf(app("com.a"), app("com.b"))
        AppTagHelper.groupListProvider = { emptyList() }
        AppTagHelper.fileTypeProvider = { IFileType.TYPE_DIR }
        val result = AppTagHelper.filterAppsByTags(apps, setOf("group_missing"))
        assertTrue(result.isEmpty())
        assertNotEquals(apps, result)
    }

    @Test
    fun twoGroupTagsWithOnlyOneResolvedYieldEmpty() {
        val known = group("known", userId = 0, path = "/snap/known")
        val installed = app("com.a", userId = 0)
        AppTagHelper.groupListProvider = { listOf(known) }
        AppTagHelper.fileTypeProvider = { IFileType.TYPE_DIR }
        val result = AppTagHelper.filterAppsByTags(
            listOf(installed),
            setOf("group_known", "group_missing"),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun xposedWithoutGroupTagsKeepsOnlyXposedApps() {
        val xposed = app("com.xposed").also { it.forceXposedModule = true }
        val plain = app("com.plain")
        val result = AppTagHelper.filterAppsByTags(
            listOf(plain, xposed),
            setOf(AppTag.TAG_XPOSED),
        )
        assertEquals(1, result.size)
        assertSame(xposed, result.single())
    }

    @Test
    fun xposedWithUnresolvedGroupTagYieldsEmpty() {
        val xposed = app("com.xposed").also { it.forceXposedModule = true }
        AppTagHelper.groupListProvider = { emptyList() }
        AppTagHelper.fileTypeProvider = { IFileType.TYPE_DIR }
        val result = AppTagHelper.filterAppsByTags(
            listOf(xposed),
            setOf(AppTag.TAG_XPOSED, "group_missing"),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun differentUserIsNotInGroupWhenDirectoryExists() {
        val installed = app("com.example", userId = 10)
        val owner = group("g", userId = 0, path = "/snap/g")
        var fileTypeCalls = 0
        AppTagHelper.fileTypeProvider = {
            fileTypeCalls++
            IFileType.TYPE_DIR
        }
        assertFalse(AppTagHelper.isAppInGroup(installed, owner))
        assertEquals(0, fileTypeCalls)
    }

    @Test
    fun sameUserIsInGroupWhenDirectoryExists() {
        val installed = app("com.example", userId = 0)
        val owner = group("g", userId = 0, path = "/snap/g")
        val seen = mutableListOf<String>()
        AppTagHelper.fileTypeProvider = { path ->
            seen += path
            if (path == "/snap/g/com.example") IFileType.TYPE_DIR else IFileType.TYPE_FILE
        }
        assertTrue(AppTagHelper.isAppInGroup(installed, owner))
        assertEquals(listOf("/snap/g/com.example"), seen)
    }

    @Test
    fun otherUserGroupTagDoesNotKeepTheCurrentUserPackage() {
        val installed = app("com.example", userId = 10)
        val owner = group("g", userId = 0, path = "/snap/g")
        AppTagHelper.groupListProvider = { listOf(owner) }
        AppTagHelper.fileTypeProvider = { IFileType.TYPE_DIR }
        val result = AppTagHelper.filterAppsByTags(listOf(installed), setOf("group_g"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun sameUserGroupTagKeepsThePackage() {
        val installed = app("com.example", userId = 0)
        val owner = group("g", userId = 0, path = "/snap/g")
        AppTagHelper.groupListProvider = { listOf(owner) }
        AppTagHelper.fileTypeProvider = { path ->
            if (path == "/snap/g/com.example") IFileType.TYPE_DIR else IFileType.TYPE_FILE
        }
        val result = AppTagHelper.filterAppsByTags(listOf(installed), setOf("group_g"))
        assertEquals(listOf(installed), result)
    }

    private fun app(pkg: String, userId: Int = 0): AppInfo =
        AppInfo(BatchTestStubFileSystem, BatchTestStubAppManager, pkg, userId)

    private fun group(id: String, userId: Int, path: String): SnapGroup =
        SnapGroup(id).detach(path = path, userId = userId)
}

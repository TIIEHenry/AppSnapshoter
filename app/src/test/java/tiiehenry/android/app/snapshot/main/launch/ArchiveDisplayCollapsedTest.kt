package tiiehenry.android.app.snapshot.main.launch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tiiehenry.android.app.snapshot.group.SnapGroup

class ArchiveDisplayCollapsedTest {

    @Test
    fun `blank query uses live collapse`() {
        assertTrue(archiveDisplayCollapsed("", true))
        assertFalse(archiveDisplayCollapsed("", false))
        assertTrue(archiveDisplayCollapsed("   ", true))
    }

    @Test
    fun `nonblank query is never collapsed`() {
        assertFalse(archiveDisplayCollapsed("we", true))
        assertFalse(archiveDisplayCollapsed("we", false))
    }

    @Test
    fun `same group blank query keeps sort mode`() {
        assertFalse(shouldExitSortModeOnBind("", groupChanged = false, isSortMode = true))
        assertFalse(shouldExitSortModeOnBind("   ", groupChanged = false, isSortMode = true))
    }

    @Test
    fun `nonblank query exits sort mode even when group id unchanged`() {
        assertTrue(shouldExitSortModeOnBind("wechat", groupChanged = false, isSortMode = true))
    }

    @Test
    fun `group change exits sort mode`() {
        assertTrue(shouldExitSortModeOnBind("", groupChanged = true, isSortMode = true))
    }

    @Test
    fun `inactive sort mode is not exited`() {
        assertFalse(shouldExitSortModeOnBind("wechat", groupChanged = true, isSortMode = false))
    }

    @Test
    fun `consume only when blank query and submitted list is archiveList identity`() {
        val archive = ArrayList<ArchiveListItem>()
        val filtered = ArrayList<ArchiveListItem>()
        assertTrue(canConsumeNavigateAfterSubmit("", archive, archive))
        assertTrue(canConsumeNavigateAfterSubmit("   ", archive, archive))
        assertFalse(canConsumeNavigateAfterSubmit("", filtered, archive))
        assertFalse(canConsumeNavigateAfterSubmit("we", archive, archive))
        assertFalse(canConsumeNavigateAfterSubmit("", archive, null))
    }

    @Test
    fun `same live group mutated apps differs by fingerprint`() {
        val group = SnapGroup("g1")
        val old = groupCard(group, fingerprint = listOf("com.a"))
        val new = groupCard(group, fingerprint = listOf("com.a", "com.b"))
        assertFalse(archiveGroupCardContentsTheSame(old, new))
    }

    @Test
    fun `same fingerprint on same group instance is unchanged`() {
        val group = SnapGroup("g1")
        val fp = listOf("com.a", "com.b")
        val old = groupCard(group, fingerprint = fp)
        val new = groupCard(group, fingerprint = fp)
        assertTrue(archiveGroupCardContentsTheSame(old, new))
    }

    @Test
    fun `name snapshot change is content change`() {
        val group = SnapGroup("g1")
        val old = groupCard(group, fingerprint = listOf("com.a"), name = "Work")
        val new = groupCard(group, fingerprint = listOf("com.a"), name = "Play")
        assertFalse(archiveGroupCardContentsTheSame(old, new))
    }

    private fun groupCard(
        group: SnapGroup,
        fingerprint: List<String>,
        name: String = "Work",
        visiblePackages: Set<String>? = null,
    ) = ArchiveListItem.GroupCard(
        group = group,
        setId = null,
        collapsed = false,
        visiblePackages = visiblePackages,
        name = name,
        appsFingerprint = fingerprint,
    )
}

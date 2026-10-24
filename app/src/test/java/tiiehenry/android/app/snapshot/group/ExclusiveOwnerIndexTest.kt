package tiiehenry.android.app.snapshot.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExclusiveOwnerIndexTest {

    @Test
    fun duplicateRowsInOneExclusiveGroupAreNotCorrupt() {
        val group = exclusive("g")
        group.row("com.app", "/g/one/com.app")
        group.row("com.app", "/g/two/com.app")
        val key = "com.app" to 0
        val owners = GroupMembershipResolver.buildExclusiveOwnerIndex(listOf(group)).getValue(key)
        assertEquals(listOf(group), owners)
        assertFalse(GroupMembershipResolver.corruptKeys(listOf(group)).contains(key))
    }

    @Test
    fun sameGroupIdIsIndexedOnceInFirstSeenOrder() {
        val first = exclusive("same")
        first.row("com.app", "/first/com.app")
        val second = exclusive("same")
        second.row("com.app", "/second/com.app")
        val key = "com.app" to 0
        val owners = GroupMembershipResolver.buildExclusiveOwnerIndex(listOf(first, second)).getValue(key)
        assertEquals(1, owners.size)
        assertSame(first, owners[0])
        assertFalse(GroupMembershipResolver.corruptKeys(listOf(first, second)).contains(key))
    }

    @Test
    fun twoExclusiveGroupsWithTheSamePackageStayCorrupt() {
        val first = exclusive("a", userId = 4)
        first.row("com.app", "/a/com.app")
        first.row("com.app", "/a/again/com.app")
        val second = exclusive("b", userId = 4)
        second.row("com.app", "/b/com.app")
        val key = "com.app" to 4
        val owners = GroupMembershipResolver.buildExclusiveOwnerIndex(listOf(first, second)).getValue(key)
        assertEquals(listOf(first, second), owners)
        assertTrue(GroupMembershipResolver.corruptKeys(listOf(first, second)).contains(key))
    }

    private fun exclusive(id: String, userId: Int = 0): SnapGroup =
        SnapGroup(id).detach(userId = userId)

    private fun SnapGroup.row(pkg: String, dir: String): ArchivedApp {
        val app = ArchivedApp(this, dir, "/icons/$pkg.png")
        apps.add(app)
        return app
    }
}

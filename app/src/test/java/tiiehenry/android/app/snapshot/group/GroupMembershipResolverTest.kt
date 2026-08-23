package tiiehenry.android.app.snapshot.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupMembershipResolverTest {

    private fun groupWith(id: String, vararg pkgs: String): SnapGroup {
        val group = SnapGroup(id)
        pkgs.forEach { pkg ->
            group.apps.add(ArchivedApp(group, "/data/$pkg", "/icons/$pkg.png"))
        }
        return group
    }

    @Test
    fun independentJoinTargets_dropsSetMembersWrongUserAndAlreadyMember() {
        val indie = groupWith("indie")
        val inSet = groupWith("inset")
        val otherUser = groupWith("other")
        val already = groupWith("already", "com.foo")
        val cards = listOf(
            JoinTargetCard(indie, setId = null, userId = 0),
            JoinTargetCard(inSet, setId = "set1", userId = 0),
            JoinTargetCard(otherUser, setId = null, userId = 10),
            JoinTargetCard(already, setId = null, userId = 0),
        )
        val result = GroupMembershipResolver.independentJoinTargets(cards, "com.foo", 0)
        assertEquals(listOf(indie), result)
    }

    @Test
    fun independentJoinTargets_preservesArchiveOrder() {
        val a = groupWith("a")
        val b = groupWith("b")
        val cards = listOf(
            JoinTargetCard(a, null, 0),
            JoinTargetCard(b, null, 0),
        )
        assertEquals(listOf(a, b), GroupMembershipResolver.independentJoinTargets(cards, "com.x", 0))
    }

    @Test
    fun membershipRows_exclusiveThenShared() {
        val ex = groupWith("ex")
        val sh = groupWith("sh")
        val rows = GroupMembershipResolver.membershipRows(
            AppGroupMembership("com.foo", 0, listOf(ex), listOf(sh))
        )
        assertEquals(2, rows.size)
        assertTrue(rows[0].exclusive && rows[0].group === ex)
        assertTrue(!rows[1].exclusive && rows[1].group === sh)
    }
}

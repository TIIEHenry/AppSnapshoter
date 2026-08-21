package tiiehenry.android.app.snapshot.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tiiehenry.android.app.snapshot.group.ArchiveRoot

class ArchiveListProjectorTest {

    private fun set(
        id: String,
        collapsed: Boolean = true,
        order: List<String> = emptyList(),
    ) = ArchiveListProjector.SetSnap(id, collapsed, order)

    private fun group(id: String, path: String) =
        ArchiveListProjector.GroupSnap(id, path)

    @Test
    fun `expanded empty set emits hint`() {
        val setA = set("sa", collapsed = false)
        val input = ArchiveListProjector.Input(
            roots = listOf(ArchiveRoot.Set("sa")),
            setsById = mapOf("sa" to setA),
            groupsById = emptyMap(),
            membersBySetId = mapOf("sa" to emptyList()),
        )
        val items = ArchiveListProjector.project(input)
        assertEquals(2, items.size)
        assertTrue(items[0] is ArchiveListProjector.DraftItem.SetHeader)
        assertTrue(items[1] is ArchiveListProjector.DraftItem.EmptySetHint)
        assertTrue(ArchiveListProjector.assertContiguousBlocks(items))
    }

    @Test
    fun `contiguous block when set expanded`() {
        val setA = set("sa", collapsed = false, order = listOf("work", "play"))
        val g1 = group("g1", "/sets/A/work")
        val g2 = group("g2", "/sets/A/play")
        val g3 = group("g3", "/alone")
        val input = ArchiveListProjector.Input(
            roots = listOf(ArchiveRoot.Set("sa"), ArchiveRoot.Group("g3")),
            setsById = mapOf("sa" to setA),
            groupsById = mapOf("g1" to g1, "g2" to g2, "g3" to g3),
            membersBySetId = mapOf("sa" to listOf(g1, g2)),
        )
        val items = ArchiveListProjector.project(input)
        assertTrue(ArchiveListProjector.assertContiguousBlocks(items))
        assertEquals(4, items.size)
        assertTrue(items[0] is ArchiveListProjector.DraftItem.SetHeader)
        assertEquals("g1", (items[1] as ArchiveListProjector.DraftItem.GroupCard).groupId)
        assertEquals("sa", (items[1] as ArchiveListProjector.DraftItem.GroupCard).setId)
        assertEquals("g2", (items[2] as ArchiveListProjector.DraftItem.GroupCard).groupId)
        assertEquals(null, (items[3] as ArchiveListProjector.DraftItem.GroupCard).setId)
    }

    @Test
    fun `collapsed set emits only header`() {
        val setA = set("sa", collapsed = true, order = listOf("work"))
        val g1 = group("g1", "/sets/A/work")
        val input = ArchiveListProjector.Input(
            roots = listOf(ArchiveRoot.Set("sa")),
            setsById = mapOf("sa" to setA),
            groupsById = mapOf("g1" to g1),
            membersBySetId = mapOf("sa" to listOf(g1)),
        )
        val items = ArchiveListProjector.project(input)
        assertEquals(1, items.size)
        val header = items[0] as ArchiveListProjector.DraftItem.SetHeader
        assertFalse(header.expanded)
        assertEquals(1, header.groupCount)
        assertTrue(ArchiveListProjector.assertContiguousBlocks(items))
    }

    @Test
    fun `skips g entry already in a set`() {
        val setA = set("sa", collapsed = false)
        val g1 = group("g1", "/sets/A/work")
        val input = ArchiveListProjector.Input(
            roots = listOf(ArchiveRoot.Set("sa"), ArchiveRoot.Group("g1")),
            setsById = mapOf("sa" to setA),
            groupsById = mapOf("g1" to g1),
            membersBySetId = mapOf("sa" to listOf(g1)),
        )
        val items = ArchiveListProjector.project(input)
        assertEquals(2, items.size)
        assertTrue(items.none { it is ArchiveListProjector.DraftItem.GroupCard && it.setId == null })
    }

    @Test
    fun `orderGroups uses basename and appends unknown`() {
        val members = listOf(
            group("g1", "/s/b"),
            group("g2", "/s/a"),
            group("g3", "/s/c"),
        )
        val ordered = ArchiveListProjector.orderGroups(listOf("a", "missing", "c"), members)
        assertEquals(listOf("g2", "g3", "g1"), ordered.map { it.id })
    }

    @Test
    fun `deriveMembers by parent path`() {
        val sets = listOf(set("sa"), set("sb"))
        val groups = listOf(
            group("g1", "/root/A/work"),
            group("g2", "/root/B/game"),
            group("g3", "/elsewhere"),
        )
        val members = ArchiveListProjector.deriveMembers(
            sets = sets,
            groups = groups,
            setPaths = mapOf("sa" to "/root/A", "sb" to "/root/B"),
            roots = listOf(ArchiveRoot.Set("sa"), ArchiveRoot.Set("sb")),
        )
        assertEquals(listOf("g1"), members["sa"]?.map { it.id })
        assertEquals(listOf("g2"), members["sb"]?.map { it.id })
        assertTrue(members.values.flatten().none { it.id == "g3" })
    }

    @Test
    fun `reconcileRoots drops member g and appends missing independents`() {
        val reconciled = ArchiveListProjector.reconcileRoots(
            roots = listOf(
                ArchiveRoot.Set("sa"),
                ArchiveRoot.Group("g1"),
                ArchiveRoot.Group("g2"),
            ),
            allGroupIds = setOf("g1", "g2", "g3"),
            memberGroupIds = setOf("g1"),
            setIds = setOf("sa"),
        )
        assertEquals(
            listOf(
                ArchiveRoot.Set("sa"),
                ArchiveRoot.Group("g2"),
                ArchiveRoot.Group("g3"),
            ),
            reconciled,
        )
    }
}

class GroupSetMembershipTest {

    @Test
    fun `isMemberOf requires direct parent`() {
        assertTrue(GroupSetMembership.isMemberOf("/root/A/work", "/root/A"))
        assertFalse(GroupSetMembership.isMemberOf("/root/A/work/nested", "/root/A"))
        assertFalse(GroupSetMembership.isMemberOf("/root/B/work", "/root/A"))
    }

    @Test
    fun `normalize strips trailing slash`() {
        assertEquals(
            GroupSetMembership.normalizePath("/root/A"),
            GroupSetMembership.normalizePath("/root/A/"),
        )
    }

    @Test
    fun `looksLikePackageName`() {
        assertTrue(GroupSetMembership.looksLikePackageName("com.example.app"))
        assertFalse(GroupSetMembership.looksLikePackageName("work"))
        assertFalse(GroupSetMembership.looksLikePackageName(".stfolder"))
    }
}

class ArchiveRootTest {

    @Test
    fun `encode decode roundtrip`() {
        val roots = listOf(
            ArchiveRoot.Set("aa11bb"),
            ArchiveRoot.Group("cc22dd"),
        )
        val encoded = ArchiveRoot.encodeList(roots)
        assertEquals("s:aa11bb,g:cc22dd", encoded)
        assertEquals(roots, ArchiveRoot.decodeList(encoded))
    }
}

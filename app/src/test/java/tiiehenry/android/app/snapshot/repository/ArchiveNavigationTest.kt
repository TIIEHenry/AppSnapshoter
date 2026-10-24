package tiiehenry.android.app.snapshot.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import tiiehenry.android.app.snapshot.group.ArchiveRoot

class ArchiveNavigationTest {

    @Test
    fun `collapsed set and group are expanded in the list before navigate`() {
        val set = NavSet("s1", "/set", collapsed = true, groupOrder = listOf("work"))
        val group = NavGroup("g1", "/set/work", collapsed = true)
        val projected = projectExpanded("g1", listOf(set), listOf(group))

        assertNotNull(projected)
        assertFalse(set.collapsed)
        assertFalse(group.collapsed)

        var published: List<NavRow>? = null
        var seenAtNavigate: List<NavRow>? = null
        emitNavigateAfterArchiveList(
            groupId = "g1",
            projected = projected,
            writeArchiveList = { published = it },
            writeNavigate = { seenAtNavigate = published },
        )

        val list = checkNotNull(seenAtNavigate)
        assertSame(projected, list)
        val header = list.filterIsInstance<NavRow.Header>().single { it.setId == "s1" }
        val card = list.filterIsInstance<NavRow.Card>().single { it.groupId == "g1" }
        assertTrue(header.expanded)
        assertFalse(card.collapsed)
    }

    @Test
    fun `navigate is written after the archive list`() {
        val order = mutableListOf<String>()
        emitNavigateAfterArchiveList(
            groupId = "g1",
            projected = "expanded-list",
            writeArchiveList = { order += "archive:$it" },
            writeNavigate = { order += "navigate:$it" },
        )

        assertEquals(listOf("archive:expanded-list", "navigate:g1"), order)
    }

    @Test
    fun `expanded set with collapsed group submits an expanded card`() {
        val set = NavSet("s1", "/set", collapsed = false, groupOrder = listOf("work"))
        val group = NavGroup("g1", "/set/work", collapsed = true)
        var published: List<NavRow>? = null
        val projected = projectExpanded("g1", listOf(set), listOf(group))

        emitNavigateAfterArchiveList(
            groupId = "g1",
            projected = projected,
            writeArchiveList = { published = it },
            writeNavigate = { _ -> },
        )

        val list = checkNotNull(published)
        assertSame(projected, list)
        val header = list.filterIsInstance<NavRow.Header>().single()
        val card = list.filterIsInstance<NavRow.Card>().single { it.groupId == "g1" }
        assertTrue(header.expanded)
        assertFalse(card.collapsed)
        assertFalse(group.collapsed)
    }

    @Test
    fun `unknown group does not change the list and still navigates`() {
        val set = NavSet("s1", "/set", collapsed = true, groupOrder = listOf("work"))
        val group = NavGroup("g1", "/set/work", collapsed = true)
        val existing = listOf(NavRow.Header("s1", expanded = false))
        var published: List<NavRow>? = existing
        var navigated: String? = null
        var projectedCalls = 0
        val projected = expandThenProject(
            groupId = "missing",
            groups = listOf(group),
            sets = listOf(set),
            groupIdOf = { it.id },
            groupPathOf = { it.path },
            markGroupExpanded = { it.collapsed = false },
            setPathOf = { it.path },
            markSetExpanded = { it.collapsed = false },
            project = {
                projectedCalls += 1
                emptyList<NavRow>()
            },
        )

        assertNull(projected)
        assertEquals(0, projectedCalls)
        assertTrue(set.collapsed)
        assertTrue(group.collapsed)

        emitNavigateAfterArchiveList(
            groupId = "missing",
            projected = projected,
            writeArchiveList = { published = it },
            writeNavigate = { navigated = it },
        )

        assertSame(existing, published)
        assertEquals("missing", navigated)
    }
}

private class NavGroup(
    val id: String,
    val path: String,
    var collapsed: Boolean,
)

private class NavSet(
    val id: String,
    val path: String,
    var collapsed: Boolean,
    val groupOrder: List<String>,
)

private sealed class NavRow {
    data class Header(val setId: String, val expanded: Boolean) : NavRow()
    data class Card(val groupId: String, val setId: String?, val collapsed: Boolean) : NavRow()
}

private fun projectExpanded(
    groupId: String,
    sets: List<NavSet>,
    groups: List<NavGroup>,
): List<NavRow>? {
    val roots = listOf(ArchiveRoot.Set(sets.single().id))
    return expandThenProject(
        groupId = groupId,
        groups = groups,
        sets = sets,
        groupIdOf = { it.id },
        groupPathOf = { it.path },
        markGroupExpanded = { it.collapsed = false },
        setPathOf = { it.path },
        markSetExpanded = { it.collapsed = false },
        project = {
            val setsById = sets.associate { set ->
                set.id to ArchiveListProjector.SetSnap(set.id, set.collapsed, set.groupOrder)
            }
            val groupsById = groups.associate { group ->
                group.id to ArchiveListProjector.GroupSnap(group.id, group.path)
            }
            val members = ArchiveListProjector.deriveMembers(
                sets = setsById.values.toList(),
                groups = groupsById.values.toList(),
                setPaths = sets.associate { it.id to it.path },
                roots = roots,
            )
            val collapsedById = groups.associate { it.id to it.collapsed }
            ArchiveListProjector.project(
                ArchiveListProjector.Input(
                    roots = roots,
                    setsById = setsById,
                    groupsById = groupsById,
                    membersBySetId = members,
                )
            ).mapNotNull { item ->
                when (item) {
                    is ArchiveListProjector.DraftItem.SetHeader ->
                        NavRow.Header(item.setId, item.expanded)
                    is ArchiveListProjector.DraftItem.GroupCard ->
                        NavRow.Card(
                            groupId = item.groupId,
                            setId = item.setId,
                            collapsed = collapsedById.getValue(item.groupId),
                        )
                    is ArchiveListProjector.DraftItem.EmptySetHint -> null
                }
            }
        },
    )
}

package tiiehenry.android.app.snapshot.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tiiehenry.android.app.snapshot.group.ArchiveRoot

class ArchiveSearchFilterTest {

    private fun app(packageName: String, label: String) =
        ArchiveSearchFilter.SearchableApp(packageName, label)

    private fun group(
        id: String,
        name: String,
        path: String,
        apps: List<ArchiveSearchFilter.SearchableApp> = emptyList(),
    ) = ArchiveSearchFilter.SearchableGroup(id, name, path, apps)

    private fun set(
        id: String,
        name: String,
        order: List<String> = emptyList(),
    ) = ArchiveSearchFilter.SearchableSet(id, name, order)

    @Test
    fun `nonempty query with no hits returns empty`() {
        val g1 = group("g1", "Work", "/alone", listOf(app("com.foo", "Foo")))
        val items = ArchiveSearchFilter.filter(
            ArchiveSearchFilter.Input(
                query = "nomatch",
                roots = listOf(ArchiveRoot.Group("g1")),
                setsById = emptyMap(),
                groupsById = mapOf("g1" to g1),
                membersBySetId = emptyMap(),
            ),
        )
        assertEquals(emptyList<ArchiveSearchFilter.DraftItem>(), items)
    }

    @Test
    fun `independent group matches by name package or label`() {
        val byName = group("gn", "Work", "/n")
        val byPkg = group("gp", "Tools", "/p", listOf(app("com.example.wechat", "Chat")))
        val byLabel = group("gl", "Tools", "/l", listOf(app("com.other", "WeChat")))

        val nameHit = ArchiveSearchFilter.filter(independentInput("Work", byName))
        assertEquals(1, nameHit.size)
        val nameCard = nameHit[0] as ArchiveSearchFilter.DraftItem.GroupCard
        assertEquals("gn", nameCard.groupId)
        assertNull(nameCard.setId)
        assertEquals(false, nameCard.collapsed)
        assertNull(nameCard.visiblePackages)

        val pkgHit = ArchiveSearchFilter.filter(independentInput("com.example.wechat", byPkg))
        assertEquals(1, pkgHit.size)
        val pkgCard = pkgHit[0] as ArchiveSearchFilter.DraftItem.GroupCard
        assertEquals("gp", pkgCard.groupId)
        assertEquals(setOf("com.example.wechat"), pkgCard.visiblePackages)

        val labelHit = ArchiveSearchFilter.filter(independentInput("WeChat", byLabel))
        assertEquals(1, labelHit.size)
        val labelCard = labelHit[0] as ArchiveSearchFilter.DraftItem.GroupCard
        assertEquals("gl", labelCard.groupId)
        assertEquals(setOf("com.other"), labelCard.visiblePackages)
    }

    @Test
    fun `set name hit emits header and all members`() {
        val setA = set("sa", "WorkSet", order = listOf("work", "play"))
        val g1 = group("g1", "Work", "/sets/A/work")
        val g2 = group("g2", "Play", "/sets/A/play")
        val items = ArchiveSearchFilter.filter(
            ArchiveSearchFilter.Input(
                query = "WorkSet",
                roots = listOf(ArchiveRoot.Set("sa")),
                setsById = mapOf("sa" to setA),
                groupsById = mapOf("g1" to g1, "g2" to g2),
                membersBySetId = mapOf("sa" to listOf(g1, g2)),
            ),
        )
        assertEquals(3, items.size)
        val header = items[0] as ArchiveSearchFilter.DraftItem.SetHeader
        assertEquals("sa", header.setId)
        assertEquals(2, header.groupCount)
        assertEquals(true, header.expanded)
        val c1 = items[1] as ArchiveSearchFilter.DraftItem.GroupCard
        val c2 = items[2] as ArchiveSearchFilter.DraftItem.GroupCard
        assertEquals("g1", c1.groupId)
        assertEquals("g2", c2.groupId)
        assertEquals("sa", c1.setId)
        assertEquals(false, c1.collapsed)
        assertNull(c1.visiblePackages)
        assertNull(c2.visiblePackages)
        assertTrue(assertContiguousBlocks(items))
    }

    @Test
    fun `member only hit emits header and one card`() {
        val setA = set("sa", "WorkSet", order = listOf("work", "play"))
        val g1 = group("g1", "Work", "/sets/A/work")
        val g2 = group("g2", "Play", "/sets/A/play", listOf(app("com.play", "Game")))
        val items = ArchiveSearchFilter.filter(
            ArchiveSearchFilter.Input(
                query = "Play",
                roots = listOf(ArchiveRoot.Set("sa")),
                setsById = mapOf("sa" to setA),
                groupsById = mapOf("g1" to g1, "g2" to g2),
                membersBySetId = mapOf("sa" to listOf(g1, g2)),
            ),
        )
        assertEquals(2, items.size)
        val header = items[0] as ArchiveSearchFilter.DraftItem.SetHeader
        assertEquals("sa", header.setId)
        assertEquals(1, header.groupCount)
        assertEquals(true, header.expanded)
        val card = items[1] as ArchiveSearchFilter.DraftItem.GroupCard
        assertEquals("g2", card.groupId)
        assertEquals("sa", card.setId)
        assertEquals(false, card.collapsed)
        assertNull(card.visiblePackages)
        assertTrue(assertContiguousBlocks(items))
    }

    @Test
    fun `set with no hits is omitted`() {
        val setA = set("sa", "WorkSet", order = listOf("work"))
        val g1 = group("g1", "Work", "/sets/A/work", listOf(app("com.foo", "Foo")))
        val items = ArchiveSearchFilter.filter(
            ArchiveSearchFilter.Input(
                query = "zzz",
                roots = listOf(ArchiveRoot.Set("sa")),
                setsById = mapOf("sa" to setA),
                groupsById = mapOf("g1" to g1),
                membersBySetId = mapOf("sa" to listOf(g1)),
            ),
        )
        assertEquals(emptyList<ArchiveSearchFilter.DraftItem>(), items)
    }

    @Test
    fun `contiguous blocks keep group cards after set header`() {
        val setA = set("sa", "Alpha", order = listOf("work", "play"))
        val setB = set("sb", "Beta", order = listOf("game"))
        val g1 = group("g1", "Work", "/sets/A/work")
        val g2 = group("g2", "Play", "/sets/A/play")
        val g3 = group("g3", "Alone", "/alone")
        val g4 = group("g4", "Game", "/sets/B/game")
        val items = ArchiveSearchFilter.filter(
            ArchiveSearchFilter.Input(
                query = "al",
                roots = listOf(
                    ArchiveRoot.Set("sa"),
                    ArchiveRoot.Group("g3"),
                    ArchiveRoot.Set("sb"),
                ),
                setsById = mapOf("sa" to setA, "sb" to setB),
                groupsById = mapOf("g1" to g1, "g2" to g2, "g3" to g3, "g4" to g4),
                membersBySetId = mapOf("sa" to listOf(g1, g2), "sb" to listOf(g4)),
            ),
        )
        assertTrue(assertContiguousBlocks(items))
        assertEquals(4, items.size)
        assertEquals("sa", (items[0] as ArchiveSearchFilter.DraftItem.SetHeader).setId)
        assertEquals("g1", (items[1] as ArchiveSearchFilter.DraftItem.GroupCard).groupId)
        assertEquals("sa", (items[1] as ArchiveSearchFilter.DraftItem.GroupCard).setId)
        assertEquals("g2", (items[2] as ArchiveSearchFilter.DraftItem.GroupCard).groupId)
        val alone = items[3] as ArchiveSearchFilter.DraftItem.GroupCard
        assertEquals("g3", alone.groupId)
        assertNull(alone.setId)
        assertTrue(items.none { it is ArchiveSearchFilter.DraftItem.SetHeader && it.setId == "sb" })
    }

    @Test
    fun `skips g entry already in a set`() {
        val setA = set("sa", "WorkSet")
        val g1 = group("g1", "Work", "/sets/A/work")
        val items = ArchiveSearchFilter.filter(
            ArchiveSearchFilter.Input(
                query = "Work",
                roots = listOf(ArchiveRoot.Set("sa"), ArchiveRoot.Group("g1")),
                setsById = mapOf("sa" to setA),
                groupsById = mapOf("g1" to g1),
                membersBySetId = mapOf("sa" to listOf(g1)),
            ),
        )
        assertEquals(2, items.size)
        assertTrue(items.none { it is ArchiveSearchFilter.DraftItem.GroupCard && it.setId == null })
        val card = items[1] as ArchiveSearchFilter.DraftItem.GroupCard
        assertEquals("g1", card.groupId)
        assertEquals("sa", card.setId)
    }

    @Test
    fun `name hit has null visiblePackages app only has package set`() {
        val named = group("gn", "Work", "/n", listOf(app("com.foo", "Foo")))
        val appOnly = group(
            "ga",
            "Tools",
            "/a",
            listOf(app("com.wechat", "WeChat"), app("com.foo", "Foo")),
        )

        val nameCard = ArchiveSearchFilter.filter(independentInput("Work", named))
            .single() as ArchiveSearchFilter.DraftItem.GroupCard
        assertNull(nameCard.visiblePackages)

        val appCard = ArchiveSearchFilter.filter(independentInput("WeChat", appOnly))
            .single() as ArchiveSearchFilter.DraftItem.GroupCard
        assertEquals(setOf("com.wechat"), appCard.visiblePackages)
    }

    @Test
    fun `match is case insensitive`() {
        val g1 = group("g1", "WeChat", "/alone", listOf(app("com.Tencent.mm", "WeChat")))
        val items = ArchiveSearchFilter.filter(independentInput("wechat", g1))
        assertEquals(1, items.size)
        val card = items[0] as ArchiveSearchFilter.DraftItem.GroupCard
        assertEquals("g1", card.groupId)
        assertNull(card.visiblePackages)
    }

    @Test
    fun `set name hit emits members missing from groupOrder`() {
        val setA = set("sa", "WorkSet", order = listOf("work"))
        val g1 = group("g1", "Work", "/sets/A/work")
        val g2 = group("g2", "NewDir", "/sets/A/newdir")
        val items = ArchiveSearchFilter.filter(
            ArchiveSearchFilter.Input(
                query = "WorkSet",
                roots = listOf(ArchiveRoot.Set("sa")),
                setsById = mapOf("sa" to setA),
                groupsById = mapOf("g1" to g1, "g2" to g2),
                membersBySetId = mapOf("sa" to listOf(g1, g2)),
            ),
        )
        assertEquals(3, items.size)
        val header = items[0] as ArchiveSearchFilter.DraftItem.SetHeader
        assertEquals(2, header.groupCount)
        assertEquals("g1", (items[1] as ArchiveSearchFilter.DraftItem.GroupCard).groupId)
        assertEquals("g2", (items[2] as ArchiveSearchFilter.DraftItem.GroupCard).groupId)
        assertTrue(assertContiguousBlocks(items))
    }

    @Test
    fun `empty set name hit emits header and hint`() {
        val setA = set("sa", "EmptySet")
        val items = ArchiveSearchFilter.filter(
            ArchiveSearchFilter.Input(
                query = "EmptySet",
                roots = listOf(ArchiveRoot.Set("sa")),
                setsById = mapOf("sa" to setA),
                groupsById = emptyMap(),
                membersBySetId = mapOf("sa" to emptyList()),
            ),
        )
        assertEquals(2, items.size)
        val header = items[0] as ArchiveSearchFilter.DraftItem.SetHeader
        assertEquals(0, header.groupCount)
        assertEquals(true, header.expanded)
        assertTrue(items[1] is ArchiveSearchFilter.DraftItem.EmptySetHint)
        assertEquals("sa", (items[1] as ArchiveSearchFilter.DraftItem.EmptySetHint).setId)
        assertTrue(assertContiguousBlocks(items))
    }

    @Test
    fun `app only member hit uses package set`() {
        val setA = set("sa", "WorkSet", order = listOf("work", "play"))
        val g1 = group("g1", "Work", "/sets/A/work")
        val g2 = group("g2", "Play", "/sets/A/play", listOf(app("com.play.game", "Arcade")))
        val items = ArchiveSearchFilter.filter(
            ArchiveSearchFilter.Input(
                query = "com.play.game",
                roots = listOf(ArchiveRoot.Set("sa")),
                setsById = mapOf("sa" to setA),
                groupsById = mapOf("g1" to g1, "g2" to g2),
                membersBySetId = mapOf("sa" to listOf(g1, g2)),
            ),
        )
        assertEquals(2, items.size)
        val card = items[1] as ArchiveSearchFilter.DraftItem.GroupCard
        assertEquals("g2", card.groupId)
        assertEquals(setOf("com.play.game"), card.visiblePackages)
    }

    private fun independentInput(
        query: String,
        group: ArchiveSearchFilter.SearchableGroup,
    ) = ArchiveSearchFilter.Input(
        query = query,
        roots = listOf(ArchiveRoot.Group(group.id)),
        setsById = emptyMap(),
        groupsById = mapOf(group.id to group),
        membersBySetId = emptyMap(),
    )

    /**
     * 与 [ArchiveListProjector.assertContiguousBlocks] 等价：同一 setId 的
     * GroupCard 必须紧跟其 SetHeader。
     */
    private fun assertContiguousBlocks(items: List<ArchiveSearchFilter.DraftItem>): Boolean {
        var i = 0
        while (i < items.size) {
            when (val item = items[i]) {
                is ArchiveSearchFilter.DraftItem.SetHeader -> {
                    val setId = item.setId
                    i++
                    if (item.expanded) {
                        if (item.groupCount == 0) {
                            if (i >= items.size || items[i] !is ArchiveSearchFilter.DraftItem.EmptySetHint) {
                                return false
                            }
                            if ((items[i] as ArchiveSearchFilter.DraftItem.EmptySetHint).setId != setId) {
                                return false
                            }
                            i++
                        } else {
                            var count = 0
                            while (i < items.size) {
                                val next = items[i]
                                if (next !is ArchiveSearchFilter.DraftItem.GroupCard || next.setId != setId) {
                                    break
                                }
                                count++
                                i++
                            }
                            if (count != item.groupCount) return false
                        }
                    }
                }
                is ArchiveSearchFilter.DraftItem.GroupCard -> {
                    if (item.setId != null) return false
                    i++
                }
                is ArchiveSearchFilter.DraftItem.EmptySetHint -> return false
            }
        }
        return true
    }
}

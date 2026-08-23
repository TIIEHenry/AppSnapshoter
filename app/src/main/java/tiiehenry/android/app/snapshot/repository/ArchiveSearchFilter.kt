package tiiehenry.android.app.snapshot.repository

import tiiehenry.android.app.snapshot.group.ArchiveRoot

/**
 * 存档搜索过滤纯函数（不触碰 MMKV / SnapGroup）。
 * 连续块不变量：同一 setId 的 GroupCard 必须紧挨在其 SetHeader 之后。
 */
object ArchiveSearchFilter {

    data class SearchableApp(
        val packageName: String,
        val label: String,
    )

    data class SearchableGroup(
        val id: String,
        val name: String,
        val path: String,
        val apps: List<SearchableApp>,
    )

    data class SearchableSet(
        val id: String,
        val name: String,
        val groupOrder: List<String>,
    )

    data class Input(
        val query: String,
        val roots: List<ArchiveRoot>,
        val setsById: Map<String, SearchableSet>,
        val groupsById: Map<String, SearchableGroup>,
        val membersBySetId: Map<String, List<SearchableGroup>>,
    )

    sealed class DraftItem {
        data class SetHeader(
            val setId: String,
            val groupCount: Int,
            val expanded: Boolean,
        ) : DraftItem()

        data class GroupCard(
            val groupId: String,
            val setId: String?,
            val collapsed: Boolean,
            val visiblePackages: Set<String>?,
        ) : DraftItem()

        data class EmptySetHint(
            val setId: String,
        ) : DraftItem()
    }

    fun filter(input: Input): List<DraftItem> {
        val query = input.query.trim()
        val items = mutableListOf<DraftItem>()
        val memberOfSet = input.membersBySetId.flatMap { (_, members) ->
            members.map { it.id }
        }.toSet()

        for (root in input.roots) {
            when (root) {
                is ArchiveRoot.Set -> {
                    val set = input.setsById[root.setId] ?: continue
                    val members = orderedMembers(set, input.membersBySetId[set.id].orEmpty())
                    if (set.name.contains(query, ignoreCase = true)) {
                        emitFullSet(items, set.id, members)
                    } else {
                        val hits = members.mapNotNull { group ->
                            matchGroup(group, query)?.let { group to it.visiblePackages }
                        }
                        if (hits.isEmpty()) continue
                        items += DraftItem.SetHeader(
                            setId = set.id,
                            groupCount = hits.size,
                            expanded = true,
                        )
                        for ((group, visiblePackages) in hits) {
                            items += groupCard(group.id, setId = set.id, visiblePackages)
                        }
                    }
                }
                is ArchiveRoot.Group -> {
                    val group = input.groupsById[root.groupId] ?: continue
                    if (group.id in memberOfSet) continue
                    val match = matchGroup(group, query) ?: continue
                    items += groupCard(group.id, setId = null, match.visiblePackages)
                }
            }
        }
        return items
    }

    private data class GroupMatch(val visiblePackages: Set<String>?)

    /**
     * 组名命中 → `visiblePackages=null`（网格全量）；仅应用命中 → 命中包名；无命中 → null。
     */
    private fun matchGroup(group: SearchableGroup, query: String): GroupMatch? {
        if (group.name.contains(query, ignoreCase = true)) return GroupMatch(null)
        val pkgs = group.apps.mapNotNull { app ->
            if (app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
            ) {
                app.packageName
            } else {
                null
            }
        }.toSet()
        return pkgs.takeIf { it.isNotEmpty() }?.let { GroupMatch(it) }
    }

    /**
     * 按 [SearchableSet.groupOrder] + path 排当前成员，复用 [ArchiveListProjector.orderGroups]。
     */
    private fun orderedMembers(
        set: SearchableSet,
        members: List<SearchableGroup>,
    ): List<SearchableGroup> {
        val snaps = members.map { ArchiveListProjector.GroupSnap(it.id, it.path) }
        val ordered = ArchiveListProjector.orderGroups(set.groupOrder, snaps)
        val byId = members.associateBy { it.id }
        return ordered.mapNotNull { byId[it.id] }
    }

    private fun emitFullSet(
        items: MutableList<DraftItem>,
        setId: String,
        members: List<SearchableGroup>,
    ) {
        items += DraftItem.SetHeader(
            setId = setId,
            groupCount = members.size,
            expanded = true,
        )
        if (members.isEmpty()) {
            items += DraftItem.EmptySetHint(setId)
        } else {
            for (group in members) {
                items += groupCard(group.id, setId = setId, visiblePackages = null)
            }
        }
    }

    private fun groupCard(
        groupId: String,
        setId: String?,
        visiblePackages: Set<String>?,
    ) = DraftItem.GroupCard(
        groupId = groupId,
        setId = setId,
        collapsed = false,
        visiblePackages = visiblePackages,
    )
}

package tiiehenry.android.app.snapshot.repository

import tiiehenry.android.app.snapshot.group.ArchiveRoot

/**
 * 存档列表投影纯函数（不触碰 MMKV / SnapGroup）。
 * 连续块不变量：同一 setId 的 GroupCard 必须紧挨在其 SetHeader 之后。
 */
object ArchiveListProjector {

    data class SetSnap(
        val id: String,
        val isCollapsed: Boolean,
        val groupOrder: List<String>,
    )

    data class GroupSnap(
        val id: String,
        val path: String,
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
        ) : DraftItem()

        data class EmptySetHint(
            val setId: String,
        ) : DraftItem()
    }

    data class Input(
        val roots: List<ArchiveRoot>,
        val setsById: Map<String, SetSnap>,
        val groupsById: Map<String, GroupSnap>,
        /** setId → 属于该集的分组 */
        val membersBySetId: Map<String, List<GroupSnap>>,
    )

    fun project(input: Input): List<DraftItem> {
        val items = mutableListOf<DraftItem>()
        val emittedGroupIds = mutableSetOf<String>()
        val memberOfSet = input.membersBySetId.flatMap { (setId, members) ->
            members.map { it.id to setId }
        }.toMap()

        for (root in input.roots) {
            when (root) {
                is ArchiveRoot.Set -> {
                    val set = input.setsById[root.setId] ?: continue
                    val members = input.membersBySetId[root.setId].orEmpty()
                    val ordered = orderGroups(set.groupOrder, members)
                    val expanded = !set.isCollapsed
                    items += DraftItem.SetHeader(
                        setId = set.id,
                        groupCount = ordered.size,
                        expanded = expanded,
                    )
                    if (expanded) {
                        if (ordered.isEmpty()) {
                            items += DraftItem.EmptySetHint(set.id)
                        } else {
                            for (group in ordered) {
                                items += DraftItem.GroupCard(group.id, setId = set.id)
                                emittedGroupIds += group.id
                            }
                        }
                    } else {
                        ordered.forEach { emittedGroupIds += it.id }
                    }
                }
                is ArchiveRoot.Group -> {
                    val group = input.groupsById[root.groupId] ?: continue
                    if (group.id in emittedGroupIds) continue
                    if (memberOfSet.containsKey(group.id)) continue
                    items += DraftItem.GroupCard(group.id, setId = null)
                    emittedGroupIds += group.id
                }
            }
        }
        return items
    }

    /**
     * 按 groupOrder（basename）排当前成员；未出现的 basename 追加末尾。
     */
    fun orderGroups(groupOrder: List<String>, members: List<GroupSnap>): List<GroupSnap> {
        val byBasename = members.associateBy { GroupSetMembership.basename(it.path) }
        val result = mutableListOf<GroupSnap>()
        val used = mutableSetOf<String>()
        for (name in groupOrder) {
            val group = byBasename[name] ?: continue
            if (group.id in used) continue
            result += group
            used += group.id
        }
        for (group in members) {
            if (group.id !in used) {
                result += group
                used += group.id
            }
        }
        return result
    }

    /**
     * 校验连续块：每个 SetHeader 后紧跟 0..N 个同 setId 的 GroupCard。
     */
    fun assertContiguousBlocks(items: List<DraftItem>): Boolean {
        var i = 0
        while (i < items.size) {
            when (val item = items[i]) {
                is DraftItem.SetHeader -> {
                    val setId = item.setId
                    i++
                    if (item.expanded) {
                        if (item.groupCount == 0) {
                            if (i >= items.size || items[i] !is DraftItem.EmptySetHint) return false
                            if ((items[i] as DraftItem.EmptySetHint).setId != setId) return false
                            i++
                        } else {
                            var count = 0
                            while (i < items.size) {
                                val next = items[i]
                                if (next !is DraftItem.GroupCard || next.setId != setId) break
                                count++
                                i++
                            }
                            if (count != item.groupCount) return false
                        }
                    }
                }
                is DraftItem.GroupCard -> {
                    if (item.setId != null) return false
                    i++
                }
                is DraftItem.EmptySetHint -> return false
            }
        }
        return true
    }

    /**
     * 按 path 派生成员：group.parent == set.path。
     * 若多集匹配，取 [roots] 中靠前的 s:。
     */
    fun deriveMembers(
        sets: List<SetSnap>,
        groups: List<GroupSnap>,
        setPaths: Map<String, String>,
        roots: List<ArchiveRoot>,
    ): Map<String, List<GroupSnap>> {
        val setOrder = roots.mapNotNull { (it as? ArchiveRoot.Set)?.setId }
        val result = mutableMapOf<String, MutableList<GroupSnap>>()
        for (setId in setOrder) {
            result[setId] = mutableListOf()
        }
        for (set in sets) {
            result.getOrPut(set.id) { mutableListOf() }
        }
        for (group in groups) {
            val matching = sets.filter { set ->
                val setPath = setPaths[set.id] ?: return@filter false
                GroupSetMembership.isMemberOf(group.path, setPath)
            }
            if (matching.isEmpty()) continue
            val chosen = matching.minByOrNull { setOrder.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                ?: matching.first()
            result.getOrPut(chosen.id) { mutableListOf() }.add(group)
        }
        return result
    }

    /**
     * 纠偏 archiveRoots：去掉已入集的 g:；独立分组补 g:（追加末尾）。
     * 保留已有 s: 与独立 g: 的相对顺序。
     */
    fun reconcileRoots(
        roots: List<ArchiveRoot>,
        allGroupIds: Set<String>,
        memberGroupIds: Set<String>,
        setIds: Set<String>,
    ): List<ArchiveRoot> {
        val result = mutableListOf<ArchiveRoot>()
        val seenSets = mutableSetOf<String>()
        val seenGroups = mutableSetOf<String>()
        for (root in roots) {
            when (root) {
                is ArchiveRoot.Set -> {
                    if (root.setId !in setIds) continue
                    if (root.setId in seenSets) continue
                    result += root
                    seenSets += root.setId
                }
                is ArchiveRoot.Group -> {
                    if (root.groupId !in allGroupIds) continue
                    if (root.groupId in memberGroupIds) continue
                    if (root.groupId in seenGroups) continue
                    result += root
                    seenGroups += root.groupId
                }
            }
        }
        for (groupId in allGroupIds) {
            if (groupId in memberGroupIds) continue
            if (groupId in seenGroups) continue
            result += ArchiveRoot.Group(groupId)
            seenGroups += groupId
        }
        for (setId in setIds) {
            if (setId in seenSets) continue
            result += ArchiveRoot.Set(setId)
            seenSets += setId
        }
        return result
    }
}

package tiiehenry.android.app.snapshot.group

import java.nio.file.Paths

/**
 * 某应用在给定 userId 下的分组成员关系。
 */
data class AppGroupMembership(
    val packageName: String,
    val userId: Int,
    val exclusiveGroups: List<SnapGroup>,
    val sharedGroups: List<SnapGroup>,
) {
    val hasAny: Boolean get() = exclusiveGroups.isNotEmpty() || sharedGroups.isNotEmpty()

    /** 列表一行摘要：独占优先，其次共享。 */
    fun summaryLabel(ungroupedLabel: String): String {
        if (!hasAny) return ungroupedLabel
        val exclusiveNames = exclusiveGroups.map { it.name }
        val sharedNames = sharedGroups.map { it.name }
        return when {
            exclusiveNames.isNotEmpty() && sharedNames.isEmpty() ->
                exclusiveNames.joinToString(" · ")
            exclusiveNames.isEmpty() && sharedNames.isNotEmpty() ->
                sharedNames.joinToString(" · ")
            else ->
                exclusiveNames.joinToString(" · ") + " · " + sharedNames.joinToString(" · ")
        }
    }
}

data class JoinTargetCard(
    val group: SnapGroup,
    val setId: String?,
    val userId: Int,
)

data class AppsPopupGroupRow(
    val group: SnapGroup,
    val exclusive: Boolean,
)

/**
 * 独占归属纯查询。调用方不得对多 owner 使用 firstOrNull 当唯一真相。
 */
object GroupMembershipResolver {

    fun packageNameOf(archived: ArchivedApp): String =
        Paths.get(archived.packageDir).fileName.toString()

    fun containsPackage(group: SnapGroup, packageName: String): Boolean {
        synchronized(group.apps) {
            return group.apps.any { packageNameOf(it) == packageName }
        }
    }

    fun findExclusiveOwners(
        groups: List<SnapGroup>,
        packageName: String,
        userId: Int,
    ): List<SnapGroup> {
        return groups.filter { group ->
            group.isExclusive &&
                group.userId == userId &&
                containsPackage(group, packageName)
        }
    }

    fun findSharedGroups(
        groups: List<SnapGroup>,
        packageName: String,
        userId: Int,
    ): List<SnapGroup> {
        return groups.filter { group ->
            !group.isExclusive &&
                group.userId == userId &&
                containsPackage(group, packageName)
        }
    }

    fun resolveMembership(
        groups: List<SnapGroup>,
        packageName: String,
        userId: Int,
    ): AppGroupMembership {
        return AppGroupMembership(
            packageName = packageName,
            userId = userId,
            exclusiveGroups = findExclusiveOwners(groups, packageName, userId),
            sharedGroups = findSharedGroups(groups, packageName, userId),
        )
    }

    /** 唯一 owner；0 或 ≥2 均返回 null（≥2 为损坏态，勿当正常归属）。 */
    fun exclusiveOwnerOrNull(
        groups: List<SnapGroup>,
        packageName: String,
        userId: Int,
    ): SnapGroup? = findExclusiveOwners(groups, packageName, userId).singleOrNull()

    /**
     * 一次扫描构建 `packageName:userId -> membership`。
     */
    fun buildMembershipIndex(groups: List<SnapGroup>): Map<String, AppGroupMembership> {
        val exclusive = linkedMapOf<String, MutableList<SnapGroup>>()
        val shared = linkedMapOf<String, MutableList<SnapGroup>>()
        for (group in groups) {
            val members = synchronized(group.apps) { group.apps.toList() }
            for (app in members) {
                val pkg = packageNameOf(app)
                val key = "$pkg:${group.userId}"
                val map = if (group.isExclusive) exclusive else shared
                map.getOrPut(key) { mutableListOf() }.add(group)
            }
        }
        val keys = exclusive.keys + shared.keys
        return keys.associateWith { key ->
            val sep = key.lastIndexOf(':')
            val pkg = key.substring(0, sep)
            val userId = key.substring(sep + 1).toIntOrNull() ?: 0
            AppGroupMembership(
                packageName = pkg,
                userId = userId,
                exclusiveGroups = exclusive[key].orEmpty(),
                sharedGroups = shared[key].orEmpty(),
            )
        }
    }

    /**
     * 构建 `(packageName, userId) -> owners`；仅含有 ≥1 个独占成员的键。
     */
    fun buildExclusiveOwnerIndex(
        groups: List<SnapGroup>,
    ): Map<Pair<String, Int>, List<SnapGroup>> {
        val map = linkedMapOf<Pair<String, Int>, MutableList<SnapGroup>>()
        for (group in groups) {
            if (!group.isExclusive) continue
            val members = synchronized(group.apps) { group.apps.toList() }
            for (app in members) {
                val pkg = packageNameOf(app)
                val key = pkg to group.userId
                map.getOrPut(key) { mutableListOf() }.add(group)
            }
        }
        return map
    }

    fun corruptKeys(
        groups: List<SnapGroup>,
    ): Set<Pair<String, Int>> =
        buildExclusiveOwnerIndex(groups)
            .filterValues { it.size >= 2 }
            .keys

    fun membershipRows(membership: AppGroupMembership): List<AppsPopupGroupRow> =
        membership.exclusiveGroups.map { AppsPopupGroupRow(it, exclusive = true) } +
            membership.sharedGroups.map { AppsPopupGroupRow(it, exclusive = false) }

    fun independentJoinTargets(
        cards: List<JoinTargetCard>,
        packageName: String,
        userId: Int,
    ): List<SnapGroup> =
        cards.filter { it.setId == null }
            .filter { it.userId == userId }
            .filter { !containsPackage(it.group, packageName) }
            .map { it.group }
}

package tiiehenry.android.app.snapshot.repository

/**
 * 先把所属集和分组标成展开，再投影。分组的展开必须发生在 [project] 读 collapsed 之前。
 * 找不到分组时不改折叠状态，也不投影。
 */
internal fun <G, S, T> expandThenProject(
    groupId: String,
    groups: List<G>,
    sets: List<S>,
    groupIdOf: (G) -> String,
    groupPathOf: (G) -> String,
    markGroupExpanded: (G) -> Unit,
    setPathOf: (S) -> String,
    markSetExpanded: (S) -> Unit,
    project: () -> T,
): T? {
    val group = groups.find { groupIdOf(it) == groupId } ?: return null
    val set = sets.firstOrNull { candidate ->
        GroupSetMembership.isMemberOf(groupPathOf(group), setPathOf(candidate))
    }
    if (set != null) markSetExpanded(set)
    markGroupExpanded(group)
    return project()
}

/** 先写入这次投影，再写导航事件。没有新列表时只写导航，不改列表。 */
internal fun <T> emitNavigateAfterArchiveList(
    groupId: String,
    projected: T?,
    writeArchiveList: (T) -> Unit,
    writeNavigate: (String) -> Unit,
) {
    if (projected != null) writeArchiveList(projected)
    writeNavigate(groupId)
}

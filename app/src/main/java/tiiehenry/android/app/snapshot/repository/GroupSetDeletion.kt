package tiiehenry.android.app.snapshot.repository

import tiiehenry.android.app.snapshot.group.ArchiveRoot

/**
 * [success] 为 false 时登记、成员 MMKV 与 [archiveRoots] 都保持调用前的值。
 * [AppDataRepository.DeleteGroupSetMode.DELETE_FILES] 只有目录删除返回 true 才为 true。
 */
internal data class GroupSetRemovalPlan(
    val success: Boolean,
    val groups: List<String>,
    val archiveRoots: List<ArchiveRoot>,
    val clearMemberIds: List<String>,
    val clearSetLocal: Boolean,
)

internal fun planGroupSetDeletion(
    mode: AppDataRepository.DeleteGroupSetMode,
    setId: String,
    memberIds: List<String>,
    groups: List<String>,
    archiveRoots: List<ArchiveRoot>,
    deleteSetDirectory: () -> Boolean,
): GroupSetRemovalPlan {
    if (mode == AppDataRepository.DeleteGroupSetMode.DELETE_FILES) {
        if (!deleteSetDirectory()) {
            return GroupSetRemovalPlan(
                success = false,
                groups = groups,
                archiveRoots = archiveRoots,
                clearMemberIds = emptyList(),
                clearSetLocal = false,
            )
        }
    }

    val roots = archiveRoots.toMutableList()
    val setIndex = roots.indexOfFirst { it is ArchiveRoot.Set && it.setId == setId }
    roots.removeAll { it is ArchiveRoot.Set && it.setId == setId }
    val dropMembers = mode != AppDataRepository.DeleteGroupSetMode.SET_ONLY
    if (!dropMembers) {
        val insertAt = if (setIndex >= 0) setIndex else roots.size
        roots.addAll(insertAt, memberIds.map { ArchiveRoot.Group(it) })
    }
    val memberSet = memberIds.toSet()
    return GroupSetRemovalPlan(
        success = true,
        groups = if (dropMembers) groups.filterNot { it in memberSet } else groups,
        archiveRoots = roots,
        clearMemberIds = if (dropMembers) memberIds else emptyList(),
        clearSetLocal = true,
    )
}

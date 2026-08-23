package tiiehenry.android.app.snapshot.main.launch

import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.group.SnapGroupSet

/**
 * 存档 Tab 密封列表项。Adapter 只吃此类型。
 * 结构形状来自 repository `archiveList`；展示形状可来自搜索 Filter（[LauncherViewModel.displayedArchiveList]）。
 */
sealed class ArchiveListItem {
    data class SetHeader(
        val set: SnapGroupSet,
        val groupCount: Int,
        val expanded: Boolean,
        /** 投影时快照，避免 DiffUtil 读到已原地修改的同一 [SnapGroupSet] */
        val name: String,
        val accentColor: Int,
    ) : ArchiveListItem()

    data class GroupCard(
        val group: SnapGroup,
        /** null = 独立分组 */
        val setId: String?,
        /** 集内成员左侧色条；独立分组为 null */
        val accentColor: Int? = null,
        /** 投影时快照，对齐 [SetHeader.expanded]；DiffUtil 禁止读 [SnapGroup.isCollapsed] */
        val collapsed: Boolean,
        /** null = 组内全部应用；非 null = 网格只显示这些包名 */
        val visiblePackages: Set<String>? = null,
        /** 投影时组名快照，避免 DiffUtil 读 live [SnapGroup.name] */
        val name: String,
        /**
         * 投影时 apps 包名指纹。同一 [SnapGroup] 原地改 [SnapGroup.apps] 后，
         * DiffUtil 禁止再读 live `group.apps`（两边会是同一可变列表）。
         */
        val appsFingerprint: List<String>,
    ) : ArchiveListItem()

    /** 空集展开后的「在此添加分组」行 */
    data class EmptySetHint(
        val set: SnapGroupSet,
        val accentColor: Int,
    ) : ArchiveListItem()
}

/** 物化 [ArchiveListItem.GroupCard.appsFingerprint]；必须在读 [SnapGroup.apps] 时加锁。 */
internal fun archiveAppsFingerprint(group: SnapGroup): List<String> =
    synchronized(group.apps) { group.apps.map { it.appInfo.packageName } }

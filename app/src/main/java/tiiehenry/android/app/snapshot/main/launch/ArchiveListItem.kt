package tiiehenry.android.app.snapshot.main.launch

import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.group.SnapGroupSet

/**
 * 存档 Tab 密封列表项。Adapter 只吃此类型；列表形状只来自 repository 投影。
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
    ) : ArchiveListItem()

    /** 空集展开后的「在此添加分组」行 */
    data class EmptySetHint(
        val set: SnapGroupSet,
        val accentColor: Int,
    ) : ArchiveListItem()
}

package tiiehenry.android.app.snapshot.main.launch

import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.app.snapshot.group.GroupMembershipResolver
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.group.SnapGroupSet
import tiiehenry.android.app.snapshot.main.launch.group.item.PackageStatus
import tiiehenry.android.app.snapshot.utils.AppStatusHelper

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
         * 投影时 apps 指纹（包名、档数量、最新档名、制作时间、锁定、运行、安装状态）。
         * 同一 [SnapGroup] 原地改 [SnapGroup.apps] 后，DiffUtil 禁止再读 live `group.apps`。
         */
        val appsFingerprint: List<String>,
    ) : ArchiveListItem()

    /** 空集展开后的「在此添加分组」行 */
    data class EmptySetHint(
        val set: SnapGroupSet,
        val accentColor: Int,
    ) : ArchiveListItem()
}

/** 物化 [ArchiveListItem.GroupCard.appsFingerprint]；读 [SnapGroup.apps] 成员时加锁，状态在锁外采集。 */
internal fun archiveAppsFingerprint(group: SnapGroup): List<String> {
    val apps = synchronized(group.apps) { group.apps.toList() }
    return apps.map { appCellFingerprint(captureAppCellSnapshot(it)) }
}

/** 网格项内容比较：data class 不含 [ArchivedApp.archives]，必须显式带上最新档。 */
internal fun archivedAppListContentsTheSame(oldItem: ArchivedApp, newItem: ArchivedApp): Boolean {
    if (oldItem.packageDir != newItem.packageDir) return false
    if (oldItem.iconFile != newItem.iconFile) return false
    return archivedAppListFingerprint(oldItem) == archivedAppListFingerprint(newItem)
}

internal fun archivedAppListFingerprint(item: ArchivedApp): String {
    val pkg = GroupMembershipResolver.packageNameOf(item)
    val latest = item.latestArchive
    val count = synchronized(item.archives) { item.archives.size }
    return "$pkg:$count:${latest?.name.orEmpty()}:${latest?.metaInfo?.makeTime ?: 0L}"
}

/**
 * 提交到内层网格的格子。快照是 submit 当时的值；旧列表保留旧快照，
 * 这样同一 [ArchivedApp] 引用在锁定或档数量变化后仍能进入 bind。
 */
data class GroupAppRow(
    val app: ArchivedApp,
    val snapshot: AppCellSnapshot,
)

/** 格子身份按 packageDir。角标和档数量都写进提交时的快照，不在比较时回读 live 字段。 */
data class AppCellSnapshot(
    val packageDir: String,
    val packageName: String,
    val iconFile: String,
    val archiveCount: Int,
    val latestName: String,
    val makeTime: Long,
    val locked: Boolean,
    val running: Boolean,
    val packageStatus: PackageStatus,
)

internal fun appCellFingerprint(snapshot: AppCellSnapshot): String =
    "${snapshot.packageName}:${snapshot.archiveCount}:${snapshot.latestName}:${snapshot.makeTime}:" +
        "${snapshot.locked}:${snapshot.running}:${snapshot.packageStatus.name}"

internal fun captureAppCellSnapshot(item: ArchivedApp): AppCellSnapshot {
    val packageName = GroupMembershipResolver.packageNameOf(item)
    val latest = item.latestArchive
    val count = synchronized(item.archives) { item.archives.size }
    val lockName = if (item.hasAppInfo()) item.appInfo.packageName else packageName
    val packageStatus = if (item.hasAppInfo()) {
        AppStatusHelper.getPackageStatus(item)
    } else {
        PackageStatus.NOT_INSTALLED
    }
    return AppCellSnapshot(
        packageDir = item.packageDir,
        packageName = packageName,
        iconFile = item.iconFile,
        archiveCount = count,
        latestName = latest?.name.orEmpty(),
        makeTime = latest?.metaInfo?.makeTime ?: 0L,
        locked = item.group.config.isLocked(lockName),
        running = item.isRunning,
        packageStatus = packageStatus,
    )
}

internal fun appCellItemsTheSame(oldItem: AppCellSnapshot, newItem: AppCellSnapshot): Boolean =
    oldItem.packageDir == newItem.packageDir

internal fun appCellContentsTheSame(oldItem: AppCellSnapshot, newItem: AppCellSnapshot): Boolean =
    oldItem == newItem

/** 同一 packageDir 的快照字段有变化时，格子需要再次 bind 角标。 */
internal fun appCellNeedsBind(previous: AppCellSnapshot?, submitted: AppCellSnapshot): Boolean {
    if (previous == null) return true
    if (!appCellItemsTheSame(previous, submitted)) return true
    return !appCellContentsTheSame(previous, submitted)
}

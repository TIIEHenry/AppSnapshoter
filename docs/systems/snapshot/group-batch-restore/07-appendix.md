---
title: "Group 批量恢复 — 附录"
type: system
status: draft
updated: 2026-06-17
summary: "与 TimelineBatchOperator 的对照表、集成代码片段"
---

# 附录

[← 返回索引](../GROUP_BATCH_RESTORE.md)

---

## A.1 与 TimelineBatchOperator 的对照

实现 `GroupBatchRestorer` 时，逐项对照 `TimelineBatchOperator.batchRestore`（`app/.../timeline/TimelineBatchOperator.kt`）：

| 步骤 | TimelineBatchOperator | GroupBatchRestorer |
|------|----------------------|------------------|
| 置 busy | `timelineViewModel.isBatchRunning = true` | `snapshotViewModel.isBatchRunning = true` |
| 进度对话框 | `GroupItemsProgressDialog` + `setTotalProgress` | 相同 |
| 取消 | `AtomicBoolean isCancelled`；取消后当前项完成后停止 | 相同 |
| 主循环 | `while (index < size && !cancelled)` | 相同 |
| 进度文案 | label / packageName / currentItem | label / packageName / **快照名** |
| 运行时解析 | `TimelineRepository.resolveEntry(key, groups, range)` | `GroupBatchRestorePlanner.resolveTaskAt(task, groups)` |
| 快照选取 | `TimelineRepository.resolveArchive(archives, strategy)` | `ArchiveResolver.pick(archives, strategy, record)` |
| 恢复调用 | `ArchiveRestorer.restoreArchiveSuspend(...)` | 相同 |
| 成功记录 | 无 | `RestoreRecordStore.put(...)` |
| 失败处理 | `failed.add`；继续下一项 | 相同 |
| finally | `isBatchRunning = false`；`loadGroups()`；`updateDialogFinishState` | 相同 |
| 成功/失败列表 | `showErroredAppsDialog` / `showSuccessAppsDialog` | 复用或提取共用（见 5.7） |

---

## A.2 RestoreRecord 写入点

在 `ArchiveRestorer.restoreArchive` **成功返回前**（或 private 包装函数内）统一写入，避免 Restorer / 单应用路径遗漏：

```kotlin
// archive/restore/RestoreRecordWriter.kt（建议）
object RestoreRecordWriter {
    fun onRestoreSuccess(archivedApp: ArchivedApp, archiveItem: ArchiveItem) {
        RestoreRecordStore.put(
            group = archivedApp.group,
            packageName = archiveItem.appInfo.packageName,
            userId = archiveItem.appInfo.userId,
            record = RestoreRecord(
                restoredAt = System.currentTimeMillis(),
                archiveName = archiveItem.name,
                archiveMakeTime = archiveItem.metaInfo.makeTime
            )
        )
    }
}
```

调用方：

- `restoreArchiveSuspend` 成功路径
- `restoreLatest` / `restoreArchiveItem` / `restoreAdvanced` 成功路径
- `GroupBatchRestorer` **无需重复写入**（若 Restorer 层已覆盖）

---

## A.3 运行时重查（resolveTaskAt）

```kotlin
fun resolveTaskAt(
    task: GroupRestoreTask,
    groups: List<SnapGroup>
): GroupRestoreTask? {
    val group = groups.find { it.id == task.app.group.id } ?: return null
    val app = group.apps.find {
        it.appInfo.packageName == task.app.appInfo.packageName &&
            it.appInfo.userId == task.app.appInfo.userId
    } ?: return null
    val archive = synchronized(app.archives) {
        app.archives[task.archive.name]
    } ?: return null
    return task.copy(app = app, archive = archive)
}
```

执行循环内：解析为 `null` 时记入 `failed`（文案对齐 `timeline_entry_stale`），不中断整批。

---

## A.4 ArchiveResolver（共享快照选取）

```kotlin
object ArchiveResolver {
    /** 时间线：仅 NEWEST / OLDEST */
    fun pick(
        archives: Collection<ArchiveItem>,
        strategy: RestoreStrategy
    ): ArchiveItem = when (strategy) {
        RestoreStrategy.NEWEST_FIRST -> archives.maxBy { it.metaInfo.makeTime }
        RestoreStrategy.OLDEST_FIRST -> archives.minBy { it.metaInfo.makeTime }
    }

    /** Group：含 LAST_RESTORED */
    fun pick(
        archives: Collection<ArchiveItem>,
        strategy: ArchivePickStrategy,
        record: RestoreRecord?
    ): ArchiveItem { /* 见 04-business-logic §4.4 */ }
}
```

`TimelineRepository.resolveArchive` 可薄包装为 `ArchiveResolver.pick(..., RestoreStrategy)`，避免两处维护 `maxBy` / `minBy`。

---

## A.5 全局批量互斥（SnapshotViewModel）

```kotlin
// SnapshotViewModel.kt
val isBatchRunning = MutableLiveData(false)

fun tryBeginBatchOperation(): Boolean {
    if (isBatchRunning.value == true) return false
    isBatchRunning.value = true
    return true
}

fun endBatchOperation() {
    isBatchRunning.value = false
}
```

迁移：`TimelineBatchOperator` 改读 `snapshotViewModel`；`LauncherFragment` / `TimelineFragment` 均观察 `snapshotViewModel.isBatchRunning` 禁用交互。

---

## A.6 GroupActionsController 批量菜单骨架

```kotlin
binding.btnBatch.setOnClickListener { anchor ->
    PopupMenu(anchor.context, anchor).apply {
        menu.add(0, R.id.menu_batch_archive, 0, R.string.group_batch_menu_archive)
        menu.add(0, R.id.menu_batch_restore, 1, R.string.group_batch_menu_restore)
        setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_batch_archive -> archiver?.archiveAllApps(group)
                R.id.menu_batch_restore -> GroupBatchRestoreDialog.show(context, group) { scope, strategy, tasks ->
                    restorer?.execute(group, tasks, scope, strategy)
                }
            }
            true
        }
        show()
    }
}
```

`updateButtonVisibility` 中将 `btnArchiveAll` 替换为 `btnBatch`。

---

## A.7 v1.0 → v1.1 文档变更摘要

| 变更 | 说明 |
|------|------|
| 批量互斥 | 从 `LauncherViewModel.isBatchRunning` 改为 **`SnapshotViewModel` 全局锁** |
| 执行模板 | 明确以 `TimelineBatchOperator.batchRestore` 为对照实现 |
| RestoreRecord 写入 | 收敛到 `RestoreRecordWriter`，避免 Restorer 与 BatchRestorer 双写 |
| 运行时解析 | 新增 `resolveTaskAt`，对齐 `resolveEntry` |
| 快照选取 | 统一为 `ArchiveResolver`，替代分散的 `resolveArchive` |
| 附录 | 新增本章，供开发直接对照落地 |

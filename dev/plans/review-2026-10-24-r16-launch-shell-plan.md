---
title: "r16 launch-shell 修改方案"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "历史档按被点中的档恢复；排序长按只写当前组；删除失败不改列表；取消批量存档要刷新并用总耗时；角标用投影快照重绑。"
---

# r16 launch-shell 修改方案

## r16-1
- 判定：hold
- 问题类：弹出菜单里点某一条历史档会进入 `ArchiveRestorer.restoreLatest`，被点中的 `ArchiveItem` 没有参与选档。
- 不变量：图标点击（`needConfirm=false`）和确认按钮写回的都是该行 `ArchiveItem`。确认文案中的 `archiveItem.name` 与实际恢复的档是同一条。应用格在未安装时的现有入口仍恢复最新档。
- 要动的符号或文件：
  - `GroupItemAdapter.popupMenuCallback.onArchiveItemClick` 的直接执行分支，以及 `showRestoreConfirmDialog` 的确认按钮：把该行 `archiveItem` 交给普通恢复。
  - `LauncherViewModel`：增加带 `ArchiveItem` 的普通恢复入口，按该档调用恢复实现，传参方式与 `onAdvancedRestoreClicked` → `ArchiveRestorer.restoreAdvanced` 一致。`onGroupItemClicked` 继续只服务应用格未安装时的 `restoreLatest`。
  - `ArchiveItemAdapter.setupNormalModeUI` 已把该行 `ArchiveItem` 和 `needConfirm` 交给 `onItemClick`，回调形状保持不变。
- 明确不改什么：高级恢复 `onAdvancedRestoreClick`；确认框的删除中性按钮 `deleteArchive`；确认文案仍使用 `archiveItem.name`。
- 本地验证：没有现成测试。应新增断言：同一应用至少有两条档时，图标回调和确认按钮都把被点中的 `ArchiveItem` 交给上述入口，且该路径不调用 `restoreLatest`；未安装应用格的点击仍调用 `restoreLatest`。

## r16-2
- 判定：hold
- 问题类：卡片复用后，非自定义排序分组留着上一张自定义分组的排序长按，拖动会把当前卡片的包名顺序写入上一组并保存。
- 不变量：每次 `setupActions` 返回后，`btnMove` 的长按要么绑定本次传入的 `SnapGroup`，要么为 null。进入排序后，标题、`boundGroup` 和 `saveSortOrderToConfig` 的目标分组 id 都等于当前 `GroupItemAdapter.group.id`。包名列表只写入该组的 `sortConfig` 并 `save()`。
- 要动的符号或文件：
  - `GroupActionsController.setupActions`：`sortType == SORT_TYPE_CUSTOM` 时设置长按，并捕获本次的 `group`；其他排序类型执行 `binding.btnMove.setOnLongClickListener(null)`。
  - `GroupsAdapter.GroupViewHolder.saveSortOrderToConfig`：仅当传入分组的 id 与 `adapter.group.id` 相同时写入 `sortOrder`、把 `sortType` 设为自定义并 `save()`。id 不同则直接返回。
- 明确不改什么：`showSortTypePopupMenu` 在同一张卡片上切换排序类型时重设或清空长按的逻辑；`shouldExitSortModeOnBind`、搜索态禁止进入排序、`exitActiveSortModes`。
- 本地验证：没有现成测试。应新增断言：先对自定义排序组调用 `setupActions`，再对非自定义组调用后长按监听为 null；传入分组 id 与适配器当前组 id 不同时，`sortOrder` 不变且不调用 `save()`。

## r16-3
- 判定：hold
- 问题类：彻底删除应用失败时仍执行 `group.apps.remove` 和 `refresh`，自定义排序会按剩余成员重写 `sortOrder` 并 `save()`。
- 不变量：`ArchiveManager.deleteAppCompletely` 返回 false 时，`group.apps` 仍包含该项，自定义 `sortOrder` 与已保存内容保持调用前的值。返回 true 时才移除、`refresh`、`loadGroups()`。
- 要动的符号或文件：`GroupItemAdapter.deleteAppCompletely`。将 `group.apps.remove(item)`、`groupsHolder.refresh(...)`、`snapshotViewModel.loadGroups()` 移入 `success` 分支。失败分支只显示失败 Toast。`onComplete()` 仍在主线程收尾处调用。
- 明确不改什么：`GroupsAdapter.applySorting` 在成员确实变化时重写自定义顺序的逻辑；单档删除和清空存档的成员列表。
- 本地验证：没有现成测试。应新增断言：失败后 `group.apps` 仍含原项，且 `sortOrder` 与 `save()` 次数不变；成功后该项被移除并调用 `refresh`。

## r16-4
- 判定：hold
- 问题类：批量存档在单项 `onFinish` 发现已取消时不刷新分组，对话框总耗时用的是当前项的 `startTime`。
- 不变量：取消收尾与 `currentIndex >= apps.size` 的完成收尾一样调用一次 `onRefresh(group)`、`loadGroups()` 和 `endBatchOperation()`。对话框 `timeline_batch_total_time` 使用 `System.currentTimeMillis() - totalStartTime`。`SuccessSnapshotInfo.timeMillis` 仍记录该项自己的 `startTime`。取消文案和 `isCancelled=true` 的完成态保持。
- 要动的符号或文件：`GroupBatchArchiver.createSnapshotsSequentially`。把完成分支和 `onFinish` 取消分支收成同一套收尾：取消时补 `onRefresh(group)` 与 `snapshotViewModel.loadGroups()`，`updateDialogFinishState` 的时长改为 `totalStartTime`。函数开头的 `if (isCancelled.get()) return` 改为进入这套取消收尾，避免跳过刷新和 `endBatchOperation()`。
- 明确不改什么：`onSuccess` 里按项记录的耗时和体积；完成 Toast 的文案；未取消时用 `currentIndex + 1` 继续下一项。
- 本地验证：没有现成测试。应新增断言：`onFinish` 时 `isCancelled` 为 true 会调用 `onRefresh` 和 `loadGroups`，传给 `updateDialogFinishState` 的时长是批次起点 `totalStartTime` 到当前的间隔；`endBatchOperation` 只发生一次。入口处 `isCancelled` 已为 true 时同样走这套收尾。

## r16-5
- 判定：hold
- 问题类：锁、运行、安装/可更新角标只在 `bind` 写入；比较用的存档指纹不含这些状态，锁定后的 `submitList` 不会重绑。删档和清空成功后没有提交分组网格。
- 不变量：锁定变化后，该格锁角标与 `group.config.isLocked(packageName)` 一致。删档或清空成功后，该格按新的存档数量、最新档和安装/运行/锁状态重新绑定。失败的删档或清空不提交网格。格子身份仍按 `packageDir`。
- 要动的符号或文件：
  - `archiveAppsFingerprint`：投影时把 `group.config.isLocked(packageName)`、`item.isRunning`、`AppStatusHelper.getPackageStatus(item)` 写入快照字符串，并保留现有的包名、档数量、最新档名、制作时间。`archiveGroupCardContentsTheSame` 继续只比较这份 `appsFingerprint`。
  - 内层列表：`refresh` 提交的是 `group.apps` 中的同一批 `ArchivedApp`。`archivedAppListContentsTheSame` 若对新旧两项各读一次 live 字段，读到的是修改之后的值。格子项改为携带提交时的投影快照，旧列表保留旧快照；`ItemDiffCallback.areContentsTheSame` 比较快照字段。锁定、删档成功、清空成功因此能让对应格子进入 `bind`，从而重跑 `updateLockIndicator`、`updateStatusIndicator`、`updateRunningIndicator`。
  - `GroupItemAdapter.onLockStateChanged`：保留 `loadGroups()`。内层按上述快照重绑锁角标。
  - `deleteArchive` 在 `reloadArchives` 成功之后，以及 `clearAllArchives` 的成功分支：调用 `groupsHolder.refresh`，使该格用新快照重绑。
- 明确不改什么：`PAYLOAD_HIGHLIGHT` 只更新名字高亮；弹层 `ArchiveItemAdapter` 按档名的 diff；`deleteAppCompletely` 的失败语义（见 r16-3）。
- 本地验证：`ArchiveDisplayCollapsedTest` 只覆盖折叠投影，不能当作本条验收。应新增断言：锁、`isRunning` 或 `PackageStatus` 不同则 `appsFingerprint` 不同，分组卡内容比较为 false；同一 `ArchivedApp` 引用在锁或存档数量变化后再次提交时，角标绑定会再次执行；`deleteArchive` 与 `clearAllArchives` 成功会 `refresh`，失败不会。

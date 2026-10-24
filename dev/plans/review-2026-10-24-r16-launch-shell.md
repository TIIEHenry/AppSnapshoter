---
title: "r16 launch-shell 审查发现"
type: guide
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "点选历史档会按最新档恢复，分组排序长按会写错组；删除失败仍移除应用，批量取消不刷新，状态角标不重绑。"
---

# r16 launch-shell 审查发现

读完允许前缀内的 `.kt` / `.java`（含 `ArchiveDisplayCollapsedTest.kt`）。下面 5 条都来自这次读到的活码。

## r16-1
- 严重度：高
- 症状：在应用弹出菜单里点某一条历史存档（行点击会先确认该档名称，图标点击直接执行），实际写回设备的是该应用的最新档。选中的旧档不会被恢复，当前应用数据会被另一份快照覆盖。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/popup/ArchiveItemAdapter.kt:144`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/popup/ArchiveItemAdapter.kt:149`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/GroupItemAdapter.kt:241`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/GroupItemAdapter.kt:261`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/LauncherViewModel.kt:93`
- 为什么是问题：图标点击把该行 `ArchiveItem` 和 `needConfirm=false` 交给回调，行点击用 `archiveItem.name` 做确认文案。两条路径的确认/直接执行都进 `onGroupItemClicked`，而该方法只调用 `ArchiveRestorer.restoreLatest(item, ...)`，`archiveItem` 以及同时传入的 `groupId`、`packageName` 都没有参与选择。高级恢复才会把被点中的 `archiveItem` 继续往下传。因此非最新档的普通恢复会把最新档覆盖到已安装应用上。
- 方向：普通恢复按用户点中的那一条 `ArchiveItem` 执行，最新档只在用户明确选最新档时使用。

## r16-2
- 严重度：高
- 症状：分组列表滑过一个「自定义排序」分组后再滑到普通排序分组，长按后者的排序按钮会进入排序模式，标题变成前一个分组的名字；在这张卡片上拖动应用，会把当前卡片上的包名顺序写进前一个分组的排序配置并保存。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/GroupActionsController.kt:106`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/GroupsAdapter.kt:230`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/GroupsAdapter.kt:291`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/GroupsAdapter.kt:340`
- 为什么是问题：`setupActions` 每次绑定都会换点击监听，但长按监听只在 `sortType == SORT_TYPE_CUSTOM` 时设置，普通分组不会清掉复用 ViewHolder 上的旧监听。该监听捕获的是上一次绑定的 `SnapGroup`。长按后 `toggleSortMode` 把 `boundGroup` 和标题改成那个旧分组，拖动列表却来自当前卡片的 `GroupItemAdapter`。`saveSortOrderToConfig` 把这份包名列表写入旧分组的 `sortConfig` 并 `save()`。同一张卡片上从自定义改成其他排序时会把长按监听置空，滑到另一张非自定义卡片时不会。
- 方向：每次绑定都按当前分组重新设置或清空排序长按，写入排序时只用当前卡片上的分组。

## r16-3
- 严重度：中
- 症状：长按删除并选择「删除应用」后，若删除失败，仍会提示失败，但该应用会从分组网格消失；自定义排序分组还会把这个包名从已保存顺序里去掉。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/GroupItemAdapter.kt:362`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/GroupItemAdapter.kt:369`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/GroupsAdapter.kt:350`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/GroupsAdapter.kt:441`
- 为什么是问题：`deleteAppCompletely` 的成功和失败只分支了 Toast。`group.apps.remove(item)`、`refresh` 和 `loadGroups()` 在分支外面。`refresh` 对自定义排序会按剩余应用重写 `sortOrder`，集合有变化就 `group.config.save()`。失败时成员已经从当前列表拿掉，自定义顺序里的位置也被写回。
- 方向：只有删除成功才从 `group.apps` 移除并刷新；失败保持原列表和原排序。

## r16-4
- 严重度：中
- 症状：分组批量存档中途取消后，已经成功的应用不会回到分组卡片上；对话框里的总耗时显示成当前这一项的耗时，而不是整批耗时。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/GroupBatchArchiver.kt:111`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/GroupBatchArchiver.kt:125`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/GroupBatchArchiver.kt:142`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/GroupBatchArchiver.kt:207`
- 为什么是问题：全部完成后的路径会 `onRefresh(group)`、`loadGroups()`，并用 `totalStartTime` 更新对话框。`onFinish` 发现已取消时只 `endBatchOperation()`，然后用当前项的 `startTime` 调用 `updateDialogFinishState`。该方法把传入时长格式化成 `timeline_batch_total_time`。取消发生在某项回调里时 `currentIndex` 仍小于列表长度，因此总会走这条路径，已完成的存档留在成功列表里，分组网格保持开始前的样子。
- 方向：取消收尾与正常结束一样刷新分组，总耗时用 `totalStartTime`。

## r16-5
- 严重度：中
- 症状：锁定应用后网格上的锁角标不变；在弹出菜单里删除或清空存档后，该应用格上的安装/可更新角标仍是操作前的状态。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/ArchiveListItem.kt:59`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/GroupItemAdapter.kt:168`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/GroupItemAdapter.kt:193`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/GroupItemAdapter.kt:283`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/GroupItemAdapter.kt:336`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/GroupItemAdapter.kt:343`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/GroupItemAdapter.kt:431`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/GroupsAdapter.kt:529`
- 为什么是问题：锁、运行中、安装/可更新都只在 `bind` 里写到 ImageView。应用格内容比较只看 `packageDir`、`iconFile` 和存档指纹（包名、档数量、最新档名、制作时间）；分组卡比较用的是同一份指纹。锁定回调虽然调用了 `refresh` → `submitList`，指纹不变就不会重绑，锁角标保持旧图。删除单档和清空全部存档只改弹层列表或调用 `reloadArchives`，没有 `refresh`，分组卡也不会因为指纹变化而重绑。
- 方向：内容比较纳入锁定、安装和运行状态；删档或清空成功后提交该应用格，使角标按新状态重绑。

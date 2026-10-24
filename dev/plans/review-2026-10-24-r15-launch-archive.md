---
title: "r15 launch-archive 审查发现"
type: guide
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "批处理期间包锁被跳过、恢复强制停止无效、失败后进度框可被返回键关掉，共 5 条。"
---

# r15 launch-archive 审查发现

读完 `makearchive`、`config`、`batch` 以及 `batch` 单元测试中的 `.kt`/`.java`。下面 5 条都来自这些活码。

## r15-1
- 严重度：高
- 症状：全局批处理还在跑时，再发起的单应用快照会和批处理同时读写同一个应用目录；批处理结束后，这条快照仍然不占包锁。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/makearchive/SnapshotCreator.kt:101`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/makearchive/SnapshotCreator.kt:302`
- 为什么是问题：`isGlobalBatchRunning()` 为真时直接把 `acquiredPackage` 当成成功，不调用 `tryBeginPackageOp`，`finally` 里也不调用 `endPackageOp`。批处理自己的快照因此能跑完，但同一进程里任何其它 `createSnapshot` 也会走这条旁路。两路可以同时给同一 `packageDir` 挂起、打包或和正在进行的恢复叠在一起，快照或应用数据会被写乱。批处理先结束时，这条仍在跑的快照继续不持有包锁，后续操作可以再进入同一目录。
- 方向：只在「当前调用就是已持有全局批锁的那一次」时跳过包锁，并且在整个快照结束前保住互斥。

## r15-2
- 严重度：高
- 症状：分组批量恢复里按下停止后再按强制停止，当前这个应用的恢复仍会跑完，正在使用的数据被覆盖。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/batch/GroupBatchRestorer.kt:43`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/batch/GroupBatchRestorer.kt:46`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/batch/GroupBatchRestorer.kt:63`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/batch/GroupBatchRestorer.kt:83`
- 为什么是问题：第一次取消把 `isCancelled` 置上，循环只在应用之间看这个标志，所以当前的 `restoreArchiveSuspend` 会执行到底。第二次按钮只把 `isForceCancelled` 写成 true，全文件没有任何读取。恢复会继续把存档写进该应用，用户已经要求停下的那一次覆盖停不掉。
- 方向：强制停止必须能中止当前 `restoreArchiveSuspend`，不能只留一个无人读取的标志。

## r15-3
- 严重度：高
- 症状：批量存档中某一个应用失败后，按返回键可以关掉进度框，打包和「存档后卸载」仍会继续，界面上已经没有取消按钮。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/makearchive/progress/AbstractProgressDialog.kt:75`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/makearchive/progress/GroupItemsProgressDialog.kt:25`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/makearchive/SnapshotCreator.kt:172`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/makearchive/SnapshotCreator.kt:321`
- 为什么是问题：`GroupItemsProgressDialog` 的取消只挂在自己的按钮上。`setItemException` 把对话框改成 `setCancelable(true)`，返回键走系统取消并关掉窗口，不会调用这个按钮回调，也不会把 `SnapshotCreator` 的 `isCanceled` 置上。失败路径仍然执行 `onFinish`。批量调用方收到 `onFinish` 后会接着处理后面的应用，此时进度框已经没了，用户无法再点停止，后续应用仍可能被打包并卸载。
- 方向：进度框被系统取消时要置上同一次批处理的取消标志，并在下一项开始前停住。

## r15-4
- 严重度：中
- 症状：额外压缩项已经关掉，打开编辑再确认后，下一次快照会重新打包这个路径。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/config/fragments/ExtraItemEditBottomSheet.kt:61`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/config/fragments/ExtraItemEditBottomSheet.kt:235`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/config/ExtraItemsManager.kt:122`
- 为什么是问题：编辑参数只保存名称、路径和排除列表，不保存 `isEnabled`。确认时 `ExtraCompressItem` 的第四个参数固定为 `true`，`updateExtraItem` 用这个新对象换掉原项。打包只收录 `isEnabled` 的额外项，于是用户关掉的目录在改个名字或排除规则之后又会被打进存档。
- 方向：确认时沿用原项的启用状态，新建才默认启用。

## r15-5
- 严重度：中
- 症状：文件选择器快速进入子目录时，路径栏是新目录，列表仍是上一目录；选择器关掉时若加载还没结束，会在主线程空指针。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/config/fragments/FilePickerBottomSheet.kt:214`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/config/fragments/FilePickerBottomSheet.kt:261`、`app/src/main/java/tiiehenry/android/app/snapshot/main/launch/config/fragments/FilePickerBottomSheet.kt:289`
- 为什么是问题：每次 `loadFiles` 都新开一个不受界面生命周期约束的协程，也不记录这次加载对应的路径。后返回的旧目录结果会调用 `updateUI`，标题写成当前的 `currentPath`，列表却是旧路径下的文件。用户按当前路径确认后，排除规则或额外项路径会指向另一个目录。`onDestroyView` 把 `_binding` 置空后，协程仍调用 `binding`，空指针从 `catch` 里的 `showEmptyState` 再抛出，这个 `launch` 没有异常处理。
- 方向：加载要带路径代际和界面作用域，结果回到主线程时对上当前路径并且视图还在才刷新。

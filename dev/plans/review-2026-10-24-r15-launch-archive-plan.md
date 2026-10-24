---
title: "r15 launch-archive 修改方案"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "包锁只由真正的持有者跳过，强制停止要打断当前恢复，失败后的系统取消要停住下一项，编辑额外项保留启用状态，文件列表只接受当前路径且视图还在的结果。"
---

# r15 launch-archive 修改方案

## r15-1
- 判定：hold
- 问题类：`createSnapshot` 用进程级的全局批处理标志跳过包锁，其它调用方和批处理结束后的在途快照都不持有该包的互斥。
- 不变量：一次 `createSnapshot` 在碰到 `packageDir` 到 `finally`（含 `onFinish`）结束前，要么自己成功调用过 `tryBeginPackageOp` 并在 `finally` 里 `endPackageOp`，要么调用方已为这同一个 `packageDir` 持有互斥并且晚于这次 `onFinish` 才释放。全局批处理正在跑，不能单独成为跳过包锁的理由。
- 要动的符号或文件：
  - `SnapshotCreator.createSnapshot`（接收 `IItemProgressDialog` 与 `isCanceled` 的重载）：去掉 `guard.isGlobalBatchRunning()` 则把 `acquiredPackage` 设为 true、并在 `finally` 跳过 `endPackageOp` 的分支。增加显式参数，默认表示调用方未持有该包操作权。未持有时始终 `tryBeginPackageOp(packageDir)`，失败则走现有的 `batch_operation_in_progress` 错误回调并 `return`；成功则只在 `finally` 释放这一次取得的锁。
  - 无对话框的 `createSnapshot(item, group, callback)` 保持默认，自己拿锁、自己放锁。
  - 真正已经拿着这次互斥、再进入该重载的批处理调用点：只有这一处传入“调用方持有”。该调用点把释放放在对应快照的 `onFinish` 之后。其它 `createSnapshot` 调用点保持默认。
- 明确不改什么：挂起/解除挂起、`ArchiveMaker.makeSnapshot`、`commitSnapshot`、取消标志在任务之间的检查、失败时删除未完成目录。
- 本地验证：没有对上的现成测试名。应新增断言：调用方未声明持有时，即使全局批处理已在跑，也会调用 `tryBeginPackageOp`，失败则不进入打包，成功则 `finally` 调用 `endPackageOp`；调用方声明持有时，本方法不调用 `tryBeginPackageOp` 和 `endPackageOp`；未开全局批处理时，仍是取得才释放。

## r15-2
- 判定：hold
- 本轮约束：不新造取消契约。`GroupBatchRestorer` 在强制停止时取消当前这次 `restoreArchiveSuspend` 所在的子协程，循环不再取下一项。`ArchiveRestorer` 只在覆盖应用数据的检查点调用 `ensureActive()`，由 archive 路改这一个文件，本路不要同时改它。
- 问题类：分组批量恢复的强制停止只写入 `isForceCancelled`，当前 `restoreArchiveSuspend` 收不到，循环要等它返回才不再进入下一项。
- 不变量：第一次取消只挡住下一项，当前这次恢复可以跑完。第二次强制停止要让正在执行的那次 `restoreArchiveSuspend` 停住，不再把这次存档继续写进该应用；停住之后该项不记入成功，后面的 task 不再开始。
- 要动的符号或文件：
  - `GroupBatchRestorer.execute`：强制停止按钮在 `isForceCancelled.set(true)` 之外，把取消送到当前这次恢复（取消包住 `restoreArchiveSuspend` 的子协程，或传入由该标志构成的取消信号）。`while` 在强制停止后不再取下一项。
  - `ArchiveRestorer.restoreArchiveSuspend`：增加协作取消。覆盖该应用数据的检查点看到强制停止后立即中止并抛给现有的 `catch`，使该项进入 `failed`。
- 明确不改什么：`isCancelled` 的第一次取消语义、`tryBeginBatchOperation` / `endBatchOperation`、成功与失败名单对话框、`updateDialogFinishState` 的文案。
- 本地验证：没有对上的现成测试名。应新增断言：仅 `isCancelled` 时，当前 `restoreArchiveSuspend` 仍跑完且下一项不开始；当前恢复未返回时置上 `isForceCancelled`，该次恢复中止且不记入 `succeeded`，后续 task 不调用 `restoreArchiveSuspend`。

## r15-3
- 判定：hold
- 问题类：某个批量存档项失败后，`setItemException` 把进度框改成可取消，返回键关掉窗口但不走 `btnCancel` 的 `cancelListener`，`SnapshotCreator` 的 `isCanceled` 不被置上，`onFinish` 之后的下一项仍会开始。
- 不变量：进度框因失败变成可系统取消时，这次系统取消与停止按钮进入同一个 `cancelListener`，并置上这一次批处理交给 `SnapshotCreator` 的 `isCanceled`。下一次 `createSnapshot` 开始前看到该标志就停住，不再打包、也不再执行存档后卸载。结束后 `setFinishButtonAsClose` 的系统取消只关闭窗口。
- 要动的符号或文件：
  - `GroupItemsProgressDialog`：把 `Dialog` 的系统取消接到现有 `cancelListener`。`setItemException` 把对话框设成可取消之后，返回键走这条路径。用单独状态记住按钮已切到 `setFinishButtonAsClose`，此状态下系统取消只关闭，不调用停止用的 `cancelListener`。
  - 把同一个 `GroupItemsProgressDialog` 和同一个 `isCanceled` 传进多次 `SnapshotCreator.createSnapshot` 的批量存档循环：`cancelListener` 里置上这个 `isCanceled`；每次下一项之前读取它，为真则不再调用 `createSnapshot`。
- 明确不改什么：`AbstractProgressDialog.setItemException` 的错误文案和堆栈对话框；`SnapshotCreator` 任务循环与 `checkCanceled` 的现有判断；单应用 `ItemProgressDialog` 的返回键；`onFinish` 仍在 `finally` 里发出，供循环得知当前项已结束。
- 本地验证：没有对上的现成测试名。应新增断言：`setItemException` 之后触发对话框的系统 `cancel` 会调用 `setOnCancelListener`，且不依赖 `btnCancel` 点击；`setFinishButtonAsClose` 之后系统 `cancel` 不调用停止回调；`isCanceled` 已置上时，批量循环不再调用下一次 `createSnapshot`。

## r15-4
- 判定：hold
- 问题类：编辑额外压缩项确认时构造的 `ExtraCompressItem` 把启用状态写成 true，`updateExtraItem` 用它换掉原来已关闭的项。
- 不变量：编辑确认后，写回列表的那一项 `isEnabled` 等于打开编辑时原项的 `isEnabled`。新建确认后的项为启用。名称、路径、排除列表仍以表单为准。
- 要动的符号或文件：
  - `ExtraItemEditBottomSheet.newInstance`：编辑时把原项 `isEnabled` 放进 arguments。
  - `ExtraItemEditBottomSheet.confirmEdit`：`isEditMode` 时用这份原启用状态构造结果；新建时才启用。
- 明确不改什么：`ExtraItemsManager.updateExtraItem` 的整项替换、启用开关 `onExtraItemEnabledChange`、删除、名称和路径的空值校验。
- 本地验证：没有对上的现成测试名。应新增断言：原项 `isEnabled == false` 时，确认后回调得到的 `ExtraCompressItem.isEnabled` 仍为 false；新建时为 true。

## r15-5
- 判定：hold
- 问题类：`loadFiles` 每次裸开一个不随视图取消的协程，返回时用当时的 `currentPath` 当标题、用过期的文件列表刷新；视图销毁后仍访问 `binding`。
- 不变量：列表和路径标题只接受“请求路径仍等于现在的 `currentPath`、且这次加载仍是最新一代、且 `_binding` 还在”的结果。视图销毁后，在途加载不再调用 `updateUI` 或 `showEmptyState`。
- 要动的符号或文件：
  - `FilePickerBottomSheet.loadFiles`：改为 `viewLifecycleOwner.lifecycleScope`，不要 `CoroutineScope(Dispatchers.IO).launch`。进入时记下 `requestedPath` 和递增的代际，`loadFilesInternal` 只用 `requestedPath`。回到主线程时，代际不是最新、路径已变、或 `_binding == null`，则整次结果丢掉。
  - `FilePickerBottomSheet.updateUI`：标题写与这份 `files` 对应的 `requestedPath`。过期结果不要先把标题写成新的 `currentPath` 再填上旧列表。
  - `showEmptyState` 与 `catch`：同样先看代际、路径和 `_binding`。协程取消要继续抛出，避免取消被当成空目录。
- 明确不改什么：相对路径计算、确认按钮回传 `selectedFiles`、目录在前的排序、长按多选。
- 本地验证：没有对上的现成测试名。应新增断言：后发出的旧路径结果返回时，适配器仍是新路径的文件，标题仍是新路径；`_binding == null` 时 `updateUI` 和 `catch` 里的 `showEmptyState` 都不读取 `binding`。

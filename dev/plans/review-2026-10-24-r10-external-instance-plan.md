---
title: "r10 external-instance 修改方案"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "列表按 externalId 隔离；恢复脱离页面生命周期；读取未决不回退正式槽；确认展示失败即结束操作；配置变更不记取消。"
---

# r10 external-instance 修改方案

## r10-1
- 判定：hold
- 问题类：已获同意的调用方能从列表拿到别人的 `externalId`，再更新、删除或解绑那份实例。
- 不变量：`listInstances` 只返回未绑定实例，以及本次请求 `externalId` 名下的实例；响应里不出现其他绑定的 `externalId`、账号和备注。`updateInstance`、`deleteInstance`、`unbindInstance` 仍只通过 `ownsInstance` 作用于这两类。
- 要动的符号或文件：
  - `InstanceGateway.list`：在 `InstanceStore.list()` 之后按 `request.externalId` 过滤，只保留 `externalId` 为空或等于本次请求的行，再交给 `InstanceProtocol.instanceRow`。
  - `InstanceProvider.query`：读取查询参数 `externalId` 并写入 `listInstances` 请求。参数缺省为空时，结果只剩未绑定实例。
- 明确不改什么：保持 `InstanceProtocol.ownsInstance` 仍只比较实例上的 `externalId` 和请求字符串。保持同意、目标包授权、确认页范围不变。未绑定实例仍对已授权调用方开放。调用方自己的实例仍返回账号、备注和自己的 `externalId`。
- 本地验证：应新增断言：两个已授权调用方各绑定 `e1`、`e2`，另有一条未绑定。以 `e1` 走网关 JSON 和 `ContentProvider` 列表时，结果只有未绑定和 `e1`，没有 `e2` 的 id、`externalId`、账号、备注。以 `e1` 更新、删除或解绑 `e2` 那条得到 `INSTANCE_NOT_FOUND`，槽位还在。以 `e2` 操作 `e2` 仍成功。未绑定实例仍可被列出并绑定。

## r10-2
- 判定：hold
- 问题类：实例表里的恢复挂在 Activity 的 `lifecycleScope` 上，页面重建会取消协程，已写入的应用数据没有收回。
- 不变量：从实例表发起的恢复在宿主 Activity 因配置变更销毁后仍会跑完；若恢复仍被取消，目标应用数据回到这次调用之前，或本次恢复做完。半截数据不能作为结束状态。
- 要动的符号或文件：
  - `InstanceSheet.runOp`：只有恢复走独立作用域，与 `InstanceConfirmActivity` 一样使用 `SupervisorJob` + `Dispatchers.IO`，`InstanceStore` 使用 `applicationContext`。页面销毁只关掉进度框；绑定还在时才刷新列表。
  - `InstanceStore.restore`：捕获 `CancellationException` 时，先把 `ArchiveRestorer.restoreArchiveSuspend` 已经写入目标应用的数据收回或把本次恢复做完，再继续抛出。
- 明确不改什么：保持创建、更新、编辑、删除仍用现在的 `lifecycleScope`。保持 `InstanceConfirmActivity` 里已点确认后的独立协程。保持 `MainActivity` 只锁竖屏、不增加 `configChanges`。
- 本地验证：应新增断言：恢复进行中销毁并重建宿主 Activity 后，恢复仍结束于完整成功或完整回到恢复前。进度框消失不打断恢复。同一张表上的创建、更新、删除仍随 Activity 作用域取消。

## r10-3
- 判定：hold
- 问题类：正式槽清单读取抛异常会被当成损坏，旁边的 `.previous` 会换掉已发布的新备份，调用方仍可能看到成功。
- 不变量：读取未决时保留正式槽并留下次收成。只有确认正式槽损坏时才用完整的 `.previous` 或暂存目录替换。替换失败时本次 `backup` 向调用方失败。新槽已就位且未被替换时，删除 `.previous` 失败仍可留下次清理。
- 要动的符号或文件：
  - `InstanceCatalog.backupReady`：结果分成就绪、确认不可用、读取未决。`MetaInfoHelper.read` 抛异常是读取未决；读到空或结构无效才是确认不可用。读取未决不能编码成 `false`。
  - `InstanceCatalog.reconcile`：正式槽读取未决时返回 `false`，不调用 `replaceWithFallback`，不删除正式槽。备选目录读取未决时也不选它、不删正式槽。
  - `InstanceCatalog.publishCommit`：正式槽读取未决时中止本次发布并保留正式槽与 `.previous`，不走现在 `!backupReady(finalSlot)` 里先删正式槽再移回 `.previous` 的分支。新槽已经移到正式位置后，删除 `.previous` 失败仍按清理债务处理。
  - `InstanceCatalog.replaceWithFallback`：只在正式槽确认不可用、且选中的来源确认完整时删除正式槽并移入来源。删除或移动失败要让调用方可见。
  - `InstanceStore.backup`：`published` 之后的 `reconcile` 若发生替换失败，抛出保存失败，使本次不返回成功文案。`finally` 里的 `runCatching` 不再吞掉这个失败。`backup` 本身已有异常时保留原异常。读取未决且没有改动正式槽时，`backup` 可以仍返回成功。
- 明确不改什么：保持暂存目录上 `manifestExists` 遇到异常仍视为存在，以及「清单确认缺失才删除不完整暂存」的分支。保持元数据 `.bak` / `.tmp` 在可读副本移回之前不删除的行为。
- 本地验证：应新增断言：`publishCommit` 后删除 `.previous` 返回 `false`，再读正式槽清单抛异常时，正式槽仍是新备份，`.previous` 仍在，`reconcile` 返回 `false`。正式槽清单确认缺失且 `.previous` 完整时才替换。替换中 `move` 失败时 `backup` 失败。暂存目录读取抛异常时目录还在。

## r10-4
- 判定：hold
- 问题类：切换确认的通知 Intent 没有 `FLAG_ACTIVITY_NEW_TASK`，通知和页面都打不开时操作仍停在 `confirm`。
- 不变量：用户能从通知或直接打开的确认页到达这次切换。两条都没有展示成功时，操作立刻成为 `error`，待确认项被取走，调用方当次返回和之后的 `getOperation` 都不再是 `confirm`。
- 要动的符号或文件：
  - `InstanceConfirmations.ask`：在 `PendingIntent.getActivity` 之前给 Intent 加上 `Intent.FLAG_ACTIVITY_NEW_TASK`，通知和 `startActivity` 使用这份已经带标志的 Intent。分别记录 `notify` 与 `startActivity` 是否成功。两者都失败时 `cancel` 掉该 id，状态写 `error`，code 用 `ExternalAccess.FAILED`，并让 `ask` 把失败告诉调用方。
  - `InstanceGateway.requestSwitch`：`ask` 失败时返回上述失败结果，不再返回已经写入的 `confirm`。
- 明确不改什么：保持只要通知或页面有一条展示成功，操作就仍是 `confirm`。保持 `getOperation` 的调用方匹配和 24 小时保留时间。失败不用 `CANCELLED`。
- 本地验证：应新增断言：通知用的 PendingIntent 含 `FLAG_ACTIVITY_NEW_TASK`。`notify` 抛 `SecurityException` 且 `startActivity` 抛异常时，当次响应和 `getOperation` 都是 `error` / `FAILED`，`peek` 为空。只成功一条时操作仍是 `confirm`，待确认项还在。

## r10-5
- 判定：hold
- 问题类：确认页在用户点确认前被配置变更销毁时，`onDestroy` 会把这次切换写成用户取消。
- 不变量：配置变更重建后，同一条待确认项还在，新页面能 `peek` 到并继续显示。只有用户按返回或取消时才写成 `CANCELLED`。
- 要动的符号或文件：
  - `InstanceConfirmActivity.onDestroy`：`isChangingConfigurations` 为真时不调用 `cancelPending`。返回键回调和取消按钮仍调用 `cancelPending`。
- 明确不改什么：保持 `confirming == true` 时销毁页面不写 `CANCELLED`。保持已点确认后的恢复协程。保持该 Activity 不增加 `configChanges`，待确认项仍只放内存。
- 本地验证：应新增断言：`confirming == false` 且 `isChangingConfigurations == true` 时销毁页面，pending 仍在，操作不是 `CANCELLED`，用同一 `confirmId` 再次 `peek` 能拿到原条目。按返回或取消后操作为 `CANCELLED` 且 `peek` 为空。`confirming == true` 时销毁不写 `CANCELLED`。

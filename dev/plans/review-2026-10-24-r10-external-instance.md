---
title: "r10 external-instance 审查发现"
type: guide
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "已授权调用方能靠列表里的 externalId 改删他人实例；切换确认会卡住或被页面销毁取消；收成可能用旧副本覆盖新备份。"
---

# r10 external-instance 审查发现

## r10-1
- 严重度：高
- 症状：另一个已经获同意的调用方，可以更新、删除或解绑绑定在别人 `externalId` 上的实例，从而覆盖或删掉那份备份。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/external/InstanceGateway.kt:51-54`、`app/src/main/java/tiiehenry/android/app/snapshot/external/InstanceGateway.kt:124-129`、`app/src/main/java/tiiehenry/android/app/snapshot/external/InstanceGateway.kt:157-174`、`app/src/main/java/tiiehenry/android/app/snapshot/external/InstanceProtocol.kt:20-25`、`app/src/main/java/tiiehenry/android/app/snapshot/external/InstanceProvider.kt:20-31`、`app/src/main/java/tiiehenry/android/app/snapshot/instance/InstanceStore.kt:299-304`
- 为什么是问题：归属只比较实例上的 `externalId` 和请求里的同一字符串，和调用方的包名、证书无关。`listInstances`（含 ContentProvider 查询）却把每个实例的 `externalId`、账号和备注都返回给任何一个已获同意、且目标包在允许范围内的调用方。拿到这个字符串后，`updateInstance`、`deleteInstance`、`unbindInstance` 会通过 `ownsInstance`，并且不经过确认页。
- 方向：列表只返回未绑定实例和本次 `externalId` 名下的实例，响应里不要带出其他人的 `externalId`。

## r10-2
- 严重度：高
- 症状：从实例表里恢复时，若承载页面被系统重建，恢复协程被取消，目标应用的数据可能停在半恢复状态。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/instance/InstanceSheet.kt:168-174`、`app/src/main/java/tiiehenry/android/app/snapshot/instance/InstanceStore.kt:94-112`、`app/src/main/java/tiiehenry/android/app/snapshot/external/InstanceConfirmActivity.kt:61-67`、`app/src/main/AndroidManifest.xml:36-40`
- 为什么是问题：表单里的恢复跑在 Activity 的 `lifecycleScope` 上。`MainActivity` 只锁了竖屏，没有 `android:configChanges`，深色模式、语言或字体变化会销毁 Activity 并取消这个协程。`InstanceStore.restore` 遇到 `CancellationException` 直接抛出，没有把已写入的应用数据收回。同一仓库里的确认页写明，中途取消恢复会让目标应用留下部分已恢复的数据，所以确认页用了独立协程；实例表没有这样做。
- 方向：应用内恢复改到不随 Activity 销毁而取消的作用域，并在取消时收成或回滚已写入的数据。

## r10-3
- 严重度：高
- 症状：一次已发布的实例更新之后，较新的正式槽会被旁边的 `.previous` 换掉，调用方仍可能看到更新成功。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/instance/InstanceCatalog.kt:136-139`、`app/src/main/java/tiiehenry/android/app/snapshot/instance/InstanceCatalog.kt:170-171`、`app/src/main/java/tiiehenry/android/app/snapshot/instance/InstanceCatalog.kt:201-204`、`app/src/main/java/tiiehenry/android/app/snapshot/instance/InstanceCatalog.kt:238-249`、`app/src/main/java/tiiehenry/android/app/snapshot/instance/InstanceCatalog.kt:284-307`、`app/src/main/java/tiiehenry/android/app/snapshot/instance/InstanceStore.kt:212-229`
- 为什么是问题：`publishCommit` 在新槽已经就位后删除 `.previous`，删除失败仍返回成功，旧副本可以留在旁边。随后 `backup` 的 `finally` 或下一次 `guarded` 会 `reconcile`。`backupReady` 在清单读取抛异常时返回 false，正式槽会被当成不可用；`replaceWithFallback` 先删掉当前目录，再把 `.previous` 移回来。同文件对暂存目录写明，暂时读失败不能当成损坏来删（`manifestExists` 遇到异常保持为存在）。正式槽没有这层区分，已发布的较新备份会被旧副本覆盖。`reconcile` 包在 `runCatching` 里，覆盖发生后 `backup` 仍返回成功。
- 方向：读取失败时保留正式槽并重试；只有确认旧槽损坏时才用 `.previous` 替换，且替换失败不能再向调用方报成功。

## r10-4
- 严重度：中
- 症状：外部切换会一直停在 `confirm`，用户看不到确认页，调用方轮询也结束不了这次操作。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/external/InstanceConfirmations.kt:77-100`、`app/src/main/java/tiiehenry/android/app/snapshot/external/InstanceGateway.kt:94-97`、`app/src/main/java/tiiehenry/android/app/snapshot/external/InstanceGateway.kt:176-188`、`app/src/main/java/tiiehenry/android/app/snapshot/external/InstanceGateway.kt:262-264`、`app/src/main/java/tiiehenry/android/app/snapshot/external/InstanceConfirmations.kt:48-57`
- 为什么是问题：`ask` 先用当前 Intent 创建通知的 `PendingIntent`，之后才给同一个对象加上 `FLAG_ACTIVITY_NEW_TASK` 并 `startActivity`。通知里的副本没有这个标志。通知权限不足时 `notify` 抛出的 `SecurityException`，以及后台 `startActivity` 的失败，都被吃掉。`requestSwitch` 仍然写入并返回 `confirm`。没有页面被打开时，没人调用 `peek`，待确认项不会被标成取消；`getOperation` 在 24 小时内一直返回这次 `confirm`。
- 方向：创建 `PendingIntent` 之前加上 `FLAG_ACTIVITY_NEW_TASK`；通知和页面都没有展示成功时，立刻把该操作标成失败。

## r10-5
- 严重度：中
- 症状：确认页还没点确认，页面被系统重建后，这次切换被记成用户取消。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/external/InstanceConfirmActivity.kt:104-115`、`app/src/main/java/tiiehenry/android/app/snapshot/external/InstanceConfirmations.kt:60-64`、`app/src/main/AndroidManifest.xml:60-64`
- 为什么是问题：`confirming` 仍为 false 时，`onDestroy` 会 `cancelPending`，把待确认项移除并写成 `CANCELLED`。该 Activity 只锁竖屏，没有 `android:configChanges`。深色模式、语言或字体变化会先销毁旧页，新页再 `peek` 时条目已经没了，确认页直接结束。用户没有点取消，调用方却收到取消。
- 方向：配置变更重建时保留待确认项，只在用户按下返回或取消时写入 `CANCELLED`。

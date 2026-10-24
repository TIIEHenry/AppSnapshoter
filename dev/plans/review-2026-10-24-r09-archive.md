---
title: "r09 archive 审查发现"
type: guide
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "额外项重名会在恢复时覆盖用户目录，权限失败仍报成功，高级恢复在后台线程刷新界面，删除失败却返回成功，额外项名称可逃出存档目录。"
---

# r09 archive 审查发现

通读 `app/src/main/java/tiiehenry/android/app/snapshot/archive` 下全部 18 个 `.kt`/`.java` 源码。下面 5 条。

## r09-1
- 严重度：高
- 症状：额外项与另一个额外项或与 `data`/`user`/`user_de`/`obb`/`media` 同名时，先登记的那份不会进快照；和内置目录同名时备份仍会成功，恢复却把内置数据解压到用户填写的额外路径上。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/archive/make/ArchiveMaker.kt:106`、`app/src/main/java/tiiehenry/android/app/snapshot/archive/make/ArchiveMaker.kt:137-165`、`app/src/main/java/tiiehenry/android/app/snapshot/archive/make/ArchiveMaker.kt:247`、`app/src/main/java/tiiehenry/android/app/snapshot/archive/make/ArchiveMaker.kt:422-436`、`app/src/main/java/tiiehenry/android/app/snapshot/archive/restore/RestorePreflight.kt:45-56`、`app/src/main/java/tiiehenry/android/app/snapshot/archive/restore/DataRestorer.kt:173-180`
- 为什么是问题：额外项先以名称为键放进 `tasks` 和 `extraItemsMap`。后面的内置项用同一个键再 `put` 一次，LinkedHashMap 会丢掉额外项的压缩任务，但 `extraItemsMap` 仍指向该名称的 json。内置目录存在时，实际写入的是内置数据的描述符和压缩包，校验能通过。全量恢复会把这份描述符既恢复到应用目录，又按额外路径再解压一次；非 `sourceFile` 直接解压到目标路径，而恢复注释写明解压器会替换该目标，用户的额外目录因此被换成应用数据。两个额外项同名时同样只留下后一个，前一个不会出现在清单里，备份也不报错。
- 方向：额外项名称与内置项或其他额外项冲突时直接失败，不要覆盖已有任务或清单项。

## r09-2
- 严重度：高
- 症状：恢复结束后提示成功，但 AppOps 已被重置为默认；曾经被记成不可变更的权限不会写回原来的模式，授权或设置模式失败也同样被当成成功。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/archive/restore/PermissionRestorer.kt:70-106`、`app/src/main/java/tiiehenry/android/app/snapshot/archive/restore/ArchiveRestorer.kt:276-281`、`app/src/main/java/tiiehenry/android/app/snapshot/archive/restore/ArchiveRestorer.kt:367-371`
- 为什么是问题：`restorePermissions` 先调用 `resetAppOps`，再逐条授权。名称已在 `fixed_permissions.json` 里时，`return@forEach` 把 `setOpsMode` 一起跳过，重置后的默认模式就留在设备上。授权和 `setOpsMode` 的异常都包在 `runCatching` 里，函数正常返回。调用方随后弹出恢复成功，不会区分权限没有写回。
- 方向：重置 AppOps 之后必须把存档里的模式写回；固定权限不能跳过模式恢复，任一失败都不要报恢复成功。

## r09-3
- 严重度：中
- 症状：高级恢复无论成功还是失败，收尾刷新都跑在 IO 线程上，列表行的状态图标更新会抛线程异常。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/archive/restore/ArchiveRestorer.kt:142`、`app/src/main/java/tiiehenry/android/app/snapshot/archive/restore/ArchiveRestorer.kt:167-168`、`app/src/main/java/tiiehenry/android/app/snapshot/archive/restore/ArchiveRestorer.kt:117-120`、`app/src/main/java/tiiehenry/android/app/snapshot/archive/restore/ArchiveRestorer.kt:209-212`
- 为什么是问题：`restoreAdvanced` 在 `Dispatchers.IO` 上启动，`finally` 里直接调用 `updateCurrent`。同文件的 `restoreArchiveItem` 和 `restoreLatest` 都会先 `withContext(Dispatchers.Main)` 再调用同一个回调。这个回调负责刷新当前行，在后台线程改 View 会触发 CalledFromWrongThreadException，成功提示弹出后协程仍会失败。
- 方向：高级恢复的 `finally` 与另外两条路径一样，切回主线程再调用 `updateCurrent`。

## r09-4
- 严重度：中
- 症状：清空全部存档或删除整个应用时，文件系统删除失败仍提示成功，存档或应用目录还在。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/archive/manage/ArchiveManager.kt:114-118`、`app/src/main/java/tiiehenry/android/app/snapshot/archive/manage/ArchiveManager.kt:140-142`、`app/src/main/java/tiiehenry/android/app/snapshot/archive/manage/ArchiveManager.kt:196-199`
- 为什么是问题：`fs.delete` 的返回值是布尔值，单份存档删除会把 `false` 和异常当成失败。`clearAllArchives` 不看每次删除的结果，循环结束后固定返回 `true`。`deleteAppCompletely` 同样不看应用目录和图标两次删除的结果。调用方按 `true` 弹出删除成功。
- 方向：只有全部 `fs.delete` 都返回 true 时，清空和彻底删除才返回成功。

## r09-5
- 严重度：高
- 症状：额外项名称里带有 `..` 或路径分隔符时，压缩包和描述符会写到存档目录外面。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/archive/make/ArchiveMaker.kt:143-148`、`app/src/main/java/tiiehenry/android/app/snapshot/archive/MetaInfoHelper.kt:52-54`
- 为什么是问题：额外项名称原样拼进 `"$name$extension"`，再 `Paths.get(archiveDir, fileName)`。名称含 `..` 时，这个路径会离开 `archiveDir`，压缩任务按该路径写出。完成回调又用同一个名称调用 `saveDataItem`，`File(archiveDir, name + ".json")` 同样能逃出目录。压缩走具备 root 能力的文件系统，逃逸写会覆盖存档目录以外的文件。
- 方向：额外项名称只允许作为存档目录内的单个路径分量，拒绝空段、分隔符和 `..`。

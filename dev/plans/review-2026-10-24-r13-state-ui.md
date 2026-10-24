---
title: "r13 state-ui 审查发现"
type: guide
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "分组集扫描会清掉仍在磁盘上的登记，移动会删掉对不上的目标存档，删除失败仍注销，拉包失败被当成目录已加载，折叠集跳转早于列表展开。"
---

# r13 state-ui 审查发现

## r13-1
- 严重度：高
- 症状：刷新分组集、修改集路径或新建集时，只要列目录失败、某个子目录 stat 失败，或子目录名以 `.` 开头，对应分组登记会被清掉；以 `.` 开头的目录之后也不会再被扫回来。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/repository/AppDataRepository.kt:525`、`app/src/main/java/tiiehenry/android/app/snapshot/repository/AppDataRepository.kt:544`、`app/src/main/java/tiiehenry/android/app/snapshot/repository/AppDataRepository.kt:581`、`app/src/main/java/tiiehenry/android/app/snapshot/repository/AppDataRepository.kt:508`、`app/src/main/java/tiiehenry/android/app/snapshot/repository/AppDataRepository.kt:630`、`app/src/main/java/tiiehenry/android/app/snapshot/repository/AppDataRepository.kt:884`
- 为什么是问题：`discoverGroupsLocked` 用 `foundBasenames` 判断「子目录还在」，但这份名单不是目录全量。`listDir` 抛错或返回 null 时名单为空；`fileType` 抛错时该名字被 `continue` 掉；名字以 `.` 开头时在检查 `group.json` 之前就跳过。随后 `toRemove` 对父路径属于该集、且 basename 不在名单里的分组执行 `config.mmkv.clearAll()`，并从 `GlobalConfig.groups` 删除。`groupOrder` 也会按这份名单改写并 `save()`。列目录失败时，磁盘上仍在的成员会整批失去 id 和 MMKV；点号目录即使带着 `group.json` 也不会进入名单，同一次扫描不会重新登记，以后的扫描同样会跳过它。`addGroupSet`、`refreshGroupSet`、`updateGroupSetPath` 都会走这条扫描。
- 方向：只有确认该子目录不存在时才注销；列目录或 stat 失败应中止本次扫描并保留原登记与 groupOrder。

## r13-2
- 严重度：高
- 症状：独占分组之间移动应用时，目标包目录只要缺了源里的某个文件或大小不一致，整个目标目录会被删掉，其中仅存在于目标的存档一并消失；删除发生在移动成功之前。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/repository/AppDataRepository.kt:1175`、`app/src/main/java/tiiehenry/android/app/snapshot/repository/AppDataRepository.kt:1296`、`app/src/main/java/tiiehenry/android/app/snapshot/repository/AppDataRepository.kt:1310`、`app/src/main/java/tiiehenry/android/app/snapshot/repository/AppDataRepository.kt:1187`
- 为什么是问题：`isCompleteRelativeTo` 只检查源的每个相对路径在目标里都有相同长度，不检查目标是否还有别的文件。`isIncompleteRelativeTo` 在目标非空且不满足该条件时为真，调用处就 `fileSystem.delete` 整个目标。目标里路径不同、或同一路径大小不同的存档会被删掉。这次删除在 `move` / `copyRecursively` 之前，后面的移动或复制失败时，目标侧文件已经不在。复制已经成功但删除源目录失败时，函数返回 `Error`，不回滚目标，也不写移动元数据；下次重试时目标已包含全部源文件，会落到 `TargetNonEmpty`，移动停在两边各一份的状态。
- 方向：只删除空目录或确认为源的子集的残留；目标含有源里没有的文件时返回 `TargetNonEmpty`，复制后删源失败则回滚目标或留下可继续完成的状态。

## r13-3
- 严重度：高
- 症状：以删除文件的方式删除分组集时，文件删除失败或抛错后，子分组登记和 MMKV 已经清除，完成回调仍会执行。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/repository/AppDataRepository.kt:677`、`app/src/main/java/tiiehenry/android/app/snapshot/repository/AppDataRepository.kt:691`、`app/src/main/java/tiiehenry/android/app/snapshot/repository/GroupRemoval.kt:4`
- 为什么是问题：`DELETE_FILES` 与 `SET_AND_GROUPS` 共用分支，先改 `GlobalConfig.groups` 并对每个成员 `mmkv.clearAll()`，再调用 `fileSystem.delete`，返回值没有使用。删除返回 false 时文件还在，登记已经没了，后面仍会 `clearLocal`、重新加载并回调完成。删除抛错时，登记和 MMKV 已写完，`archiveRoots` 更新和 `reloadGroupsLocked` 不会执行，`catch` 里同样调用 `onComplete`。同模块的 `removeRegisteredGroup` 是删除返回成功后才注销，`deleteGroup` 走的是那条路径。
- 方向：`DELETE_FILES` 仅在 `fileSystem.delete` 返回 true 后清除登记，失败时保持原登记并让调用方看到失败。

## r13-4
- 严重度：中
- 症状：某个用户的已安装包列表拉取失败时，应用目录仍被标成已加载，该用户从 `appsList` 消失。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/repository/AppDataRepository.kt:365`、`app/src/main/java/tiiehenry/android/app/snapshot/repository/AppDataRepository.kt:392`、`app/src/main/java/tiiehenry/android/app/snapshot/repository/AppDataRepository.kt:398`
- 为什么是问题：`userInfos` 非空时内部 map 不会返回 null。单个用户在 `getInstalledPackages` 或后续循环里抛错，只被记日志，不会写入 map。外层只要 map 非 null 就 `postValue` 并 `markAppsCatalogLoaded()`。全部用户都失败时，会提交空 map 并把「已成功拉取」设为 true，加载指示在 `finally` 里结束。界面得到的是一份已完成的部分目录或空目录。
- 方向：任一用户拉包失败时不要标记目录已加载，让这次结果保持为未完成以便重试。

## r13-5
- 严重度：中
- 症状：从时间线或应用页跳到折叠分组集里的分组时，跳转事件先发出，存档列表里还没有该分组卡片。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/SnapshotViewModel.kt:246`、`app/src/main/java/tiiehenry/android/app/snapshot/SnapshotViewModel.kt:256`、`app/src/main/java/tiiehenry/android/app/snapshot/repository/AppDataRepository.kt:699`、`app/src/main/java/tiiehenry/android/app/snapshot/repository/ArchiveListProjector.kt:66`、`app/src/main/java/tiiehenry/android/app/snapshot/repository/AppDataRepository.kt:344`
- 为什么是问题：折叠的分组集在 `ArchiveListProjector.project` 里只留下 `SetHeader`，不生成 `GroupCard`。`requestNavigateToGroup` 的注释要求先展开再滚动，但 `setGroupSetCollapsed` 只是 `scope.launch` 后改 `isCollapsed` 并 `reprojectArchiveListLocked`（`archiveList.postValue`），而 `navigateToGroup.value` 在当前调用里同步赋值。主线程上的跳转观察者先于这次投影送达时，列表里还没有目标卡片。分组自己的 `isCollapsed = false` 也没有单独提交新的 `archiveList`；投影里的 `collapsed` 是当时拷贝的布尔值。
- 方向：在同一临界区内展开分组集和分组、提交新的存档列表之后，再设置 `navigateToGroup`。

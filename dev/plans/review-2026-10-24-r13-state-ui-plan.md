---
title: "r13 state-ui 修改方案"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "扫描失败保留分组登记，移动只清子集残留，删集失败不注销，拉包失败不标已加载，跳转在列表展开后发出。"
---

# r13 state-ui 修改方案

## r13-1
- 判定：hold
- 问题类：分组集扫描把列目录失败、stat 失败和点号目录当成目录已消失，并据此注销登记、改写 groupOrder。
- 不变量：`discoverGroupsLocked` 只有在 `listDir` 成功返回、且该子目录名不在这份成功名单里时，才注销该集下的分组。`listDir` 抛错或返回 null、任一子项 `fileType` 抛错时，本次扫描不写 `GlobalConfig.groups`、不清 MMKV、不改 `groupOrder`、不 `save` 集配置。名字以 `.` 开头的目录走与普通目录相同的类型与 `group.json` 判断，能进入名单并被登记。真正扫到的空目录仍可注销已经不在磁盘上的成员。
- 要动的符号或文件：`AppDataRepository.discoverGroupsLocked`。先把名单和待登记分组收齐，全部 stat 成功后再写入 `GlobalConfig.groups`、执行 `toRemove` 的 `mmkv.clearAll()`、改 `SnapGroupSet.groupOrder` 并 `save`。`listDir` 的 `?: emptyList()` 不能再把 null 和空目录合成同一种结果。`addGroupSet`、`refreshGroupSet`、`updateGroupSetPath` 继续只调用这一处扫描。
- 明确不改什么：`looksLikePackageName` 且没有 `group.json` 的目录仍跳过，仍不进入名单。非目录项仍不登记。`upgradeEmptyGroupToSet` 在扫描前取消原空分组登记的顺序不动。`updateGroupSetPath` 在调用扫描之前已经写入的集路径、名称和强调色不回滚。
- 本地验证：应新增断言：`listDir` 抛错或返回 null 时，原 `GlobalConfig.groups`、成员 MMKV、`groupOrder` 保持调用前的值。某一子目录 `fileType` 抛错时，同一次调用不注销其它仍在名单中的成员，也不 `save` 新的 `groupOrder`。成功列出且不含某 basename 时，该成员仍被注销。名为 `.hidden` 且带 `group.json` 的目录被登记，再次扫描仍在册。无 `group.json` 的包名样子目录仍不登记。

## r13-2
- 判定：hold
- 问题类：独占分组之间移动应用时，目标只要相对源不完整就会先整目录删除，源里没有的存档一起消失；复制已成功但删源失败后，重试停在两边各一份。
- 不变量：目标目录里只要有源没有的相对路径，或同一路径长度不同，就返回 `TargetNonEmpty`，且该目录在返回前不被 `delete`。只有目标为空，或目标每个文件都能在源里找到同长度对应项并且至少缺一个源文件时，才删除目标再 `move`。两边相对路径与长度完全一致时，删除源并写移动元数据，完成这次移动，不返回 `TargetNonEmpty`。复制成功但删源失败时保留目标、不写元数据、返回 `Error`；下一次进入完全一致分支后可以删源并完成。
- 要动的符号或文件：`AppDataRepository.moveAppBetweenGroups` 里 `dstExists` 分支（现 `isCompletelyEmptyDir` / `isIncompleteRelativeTo` / `fileSystem.move`），以及 `isCompleteRelativeTo`、`isIncompleteRelativeTo`。用「完全一致」和「真子集残留」两个判断替换现在的 `isIncompleteRelativeTo` 删除条件。残留或空目录的 `delete` 只有返回 true 才继续 `move`；返回 false 则返回 `Error`。完全一致分支走与成功移动相同的 `moveGroupIcon` 和 `finalizeMoveMetadata`，不再对已存在的目标调用 `move`。
- 明确不改什么：独占、锁定、userId、Busy、`AlreadyAtTarget`（源已不在且目标目录非空）的预检不动。`move` 失败后 `copyRecursively` 失败时删除不完整目标的现有回滚不动。`copyRecursively` 的覆盖参数不动。`isCompleteRelativeTo` 在复制后用来确认源文件都已到达目标的检查不动。
- 本地验证：应新增断言：目标含源没有的文件，或同一路径长度不同时，结果为 `TargetNonEmpty`，目标文件仍在。目标为空，或目标是源的真子集时，先删除目标再移动。复制成功且 `delete(source)` 返回 false 时，结果为 `Error`，目标仍在，元数据未写；再次调用且两边文件集合与长度一致时，结果为 `Moved`，源被删除。`delete` 残留目录返回 false 时，结果为 `Error`，且未调用 `move`。

## r13-3
- 判定：hold
- 问题类：`DELETE_FILES` 与 `SET_AND_GROUPS` 共用先注销再删目录的分支，删除失败或抛错后登记和 MMKV 已清，完成回调仍执行。
- 不变量：`DELETE_FILES` 仅在 `fileSystem.delete(set.path)` 返回 true 之后，才改 `GlobalConfig.groups`、清成员 MMKV、写 `archiveRoots`、`clearLocal` 并 `reloadGroupsLocked`。返回 false 或抛错时，这些登记与集配置保持调用前的值。失败对调用方可见，不能再走现在的无差别 `onComplete`。
- 要动的符号或文件：`AppDataRepository.deleteGroupSet` 中 `DeleteGroupSetMode.DELETE_FILES` 分支，以及 `SnapshotViewModel.deleteGroupSet` 的回调传递。把 `DELETE_FILES` 从 `SET_AND_GROUPS` 的共用分支拆开：先 `delete`，成功后再注销。完成回调改为带成功与否（`(Boolean) -> Unit`）；仓库内调用点按该布尔值分支。删除抛错时外层 `catch` 报告失败，且此时锁内还没有写过登记。
- 明确不改什么：`SET_ONLY` 把子分组变成独立分组的行为不动。`SET_AND_GROUPS` 只注销、不删文件的行为不动。`removeRegisteredGroup` 与 `deleteGroup` 仍是删除返回成功后才注销，本条不改它们的实现。
- 本地验证：应新增断言：`DELETE_FILES` 且 `delete` 返回 false 时，`GlobalConfig.groups`、成员 MMKV、`archiveRoots` 与调用前一致，回调收到失败。`delete` 抛错时同样保持登记，回调收到失败。`delete` 返回 true 时成员被注销并回调成功。`SET_AND_GROUPS` 仍不调用 `delete`，且仍注销成员。

## r13-4
- 判定：hold
- 问题类：某个用户 `getInstalledPackages` 抛错时，该用户不会进入结果 map，但非空用户列表仍会把目录标成已加载。
- 不变量：`userInfos` 非空时，只要有一个用户的外层拉包抛错，这次就不 `appsList.postValue`，也不 `markAppsCatalogLoaded()`。`appsCatalogLoaded` 保持 false，已有的 `appsList` 保持原值。全部用户都抛错时同样不提交空 map。`isAppsLoading` 仍在 `finally` 里结束。
- 要动的符号或文件：`AppDataRepository.loadApps`。外层 `catch` 记下该用户失败；循环结束后有失败则跳过 `postValue` 与 `markAppsCatalogLoaded`，`succeeded` 保持 false。
- 明确不改什么：`userInfos` 为空时仍返回 null，且不标记已加载。单个包的 `getPackageInfo` 抛错仍只跳过该包，该用户仍写入 map。不改 `isAppsLoading` 的 `finally` 收尾。
- 本地验证：应新增断言：两名用户中一名 `getInstalledPackages` 抛错时，`isAppsCatalogLoaded` 为 false，`appsList` 仍是调用前的值。全部用户抛错时不出现空 map 覆盖，且已加载标记为 false。全部用户成功时才 `postValue` 并标记已加载。某一包 `getPackageInfo` 抛错时，该用户仍在 map 中，目录可以标记已加载。

## r13-5
- 判定：hold
- 问题类：跳到折叠分组集里的分组时，`navigateToGroup` 在存档列表展开并提交之前就写上了。
- 不变量：`requestNavigateToGroup` 在所属集和分组都已展开、新的存档列表已经按这个形状提交之后，才写 `navigateToGroup`。提交出去的列表里，该集的 `SetHeader` 为展开，且含有目标 `GroupCard`，卡片上的 `collapsed` 为 false。写导航事件的时候，列表值已经是这版投影。
- 要动的符号或文件：`SnapshotViewModel.requestNavigateToGroup`，以及 `AppDataRepository.setGroupSetCollapsed` 所在的锁内投影路径（`loadGroupsMutex`、`reprojectArchiveListLocked`）。在 `loadGroupsMutex` 内把集的 `isCollapsed` 和分组的 `isCollapsed` 写成展开，并生成新列表；释放锁之后，在主线程先写 `archiveList`，再写 `navigateToGroup`。不要再调用异步的 `setGroupSetCollapsed` 后立刻同步写 `navigateToGroup`。分组对象上的 `isCollapsed = false` 必须发生在这次投影读 `collapsed` 之前。
- 明确不改什么：`ArchiveListProjector.project` 在集折叠时只留 `SetHeader`、不生成 `GroupCard` 的规则不动。用户手动折叠仍走现有的 `setGroupSetCollapsed`。`navigateToGroupSet` 不强制展开。找不到分组时仍直接写 `navigateToGroup`。`collapseAllArchive` 不动。
- 本地验证：应新增断言：集与分组均为折叠时，处理导航事件时读到的 `archiveList` 已包含该 `groupId` 的 `GroupCard`，且 `collapsed == false`、对应 `SetHeader` 为展开。`navigateToGroup` 的写入发生在这次 `archiveList` 写入之后。集已展开、仅分组折叠时，同一次提交里的 `GroupCard.collapsed` 为 false。未知分组不改列表，仍发出导航事件。

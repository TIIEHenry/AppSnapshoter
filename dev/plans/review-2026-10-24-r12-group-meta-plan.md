---
title: "r12 group-meta 修改方案"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "分组重载整表替换、存档按条容错、移动失败仍提示并刷新已移动项、分组标签未解析或用户不一致时不得放行。"
---

# r12 group-meta 修改方案

## r12-1

- 判定：hold
- 问题类：`loadApps` 先清空再在另一段临界区追加，失败或并发会留下空列表、半截列表或重复包，独占索引把同一分组的重复条目当成多主。
- 不变量：一次刷新要么在同一临界区用新列表整体替换 `apps`，要么保持替换前的列表；同一独占分组、同一包在索引里只占一个 owner，两个不同独占分组同时拥有该包仍算损坏。
- 要动的符号或文件：`SnapGroup.loadApps`、`GroupMembershipResolver.buildExclusiveOwnerIndex`。把 `reload` 时的 `apps.clear()` 和后面的 `listDir` / `fileType` / `apps.add` 收进同一个 `synchronized(apps)`。先写入局部列表，`listDir` 或未捕获的 `fileType` 失败时不要动 `apps`，再把异常抛出。成功后再 `clear` + `addAll`，禁止在旧内容后面追加。`buildExclusiveOwnerIndex` 对同一个 `(packageName, userId)` 按分组 `id` 只加入一次，保留第一次出现的顺序。`corruptKeys` 继续对去重后的列表用 `size >= 2`。
- 明确不改什么：不改 `corruptKeys` 的 `>= 2` 阈值，不改 `findExclusiveOwners` / `containsPackage` 的查询语义。单条存档失败算不算缓存完整由 r12-2 收紧，这里不把存档失败当成整组目录扫描失败。
- 本地验证：应新增断言：并发下一次 `reload` 清空后、替换前另一次加载已填入同一批包，结束后每个包只有一行；`listDir` 或 `fileType` 抛错后原列表字节级不变，且 `reload=false` 不会把半截结果当完成缓存返回；同一独占分组里同一包两行时 `corruptKeys` 不含该键，两个不同独占分组含该包时 `corruptKeys` 仍含该键。

## r12-2

- 判定：hold
- 问题类：单条存档的 `fileType` 或 `readDataItems` 失败会中断后续存档，额外项任一失败被整表换成空表，应用仍被放进 `reload=false` 会直接返回的列表。
- 不变量：一条存档失败只跳过该条，其余存档照常写入；额外项保留已解析的键；未读完的应用可以留在分组里，但下一次 `loadApps(reload=false)` 必须补读，不能把半截结果当成已经加载完成。
- 要动的符号或文件：`ArchivedApp.loadArchives`、`SnapGroup.loadApps`。每个 `archiveName` 单独捕获 `fileType` 和 `readDataItems` 的异常，失败则不写入该条并继续循环。`extraItems` 按条解析，失败只丢掉该键。为每个存档记录是否读完；`!reload` 时只跳过已完整写入的名字，不完整的重读并替换该条目。`loadArchives` 在目录列举失败时保持不完整。`SnapGroup.loadApps` 里：`apps` 非空且全部完整才在 `!reload` 时早退；若有不完整的应用，在同一把 `synchronized(apps)` 里只对它们再调用 `loadArchives(false)`，不要再次 `apps.add`。`loadArchives` 抛错时仍可 `add` 已建好的应用，但必须标成不完整。
- 明确不改什么：不因一条存档失败丢掉整个应用或整组列表。不改 `userId` 与分组不一致时只打日志并继续的分支。不改 `ArchiveItem` 的字段形状。
- 本地验证：应新增断言：第二条存档的 `fileType` 或 `readDataItems` 抛错时，第一条和第三条仍在 `archives`；`extraItems` 一个键失败时其余键仍在；该应用 `archives` 不完整时，再次 `loadApps(reload=false)` 会补读失败项且 `apps` 里该包仍只有一行。

## r12-3

- 判定：hold
- 问题类：同一批里已有冲突时，`Error`、`Busy`、`CorruptMultiOwner` 不会提示；确认移动后遇到 `Busy` 则停止后续移动，且已移走的包不刷新。
- 不变量：冲突对话框之外仍按原优先级报出本批失败；`Busy` 停止后续移动；此前已有 `Moved` 时调用一次 `onMembershipChanged`。
- 要动的符号或文件：`AddAppsResultUi.handle`、`AddAppsResultUi.moveApps`。`conflicts` 非空时，先用与 `conflicts` 为空时相同的 `when` 提示一条：`CorruptMultiOwner` 优先于 `Busy`，再是第一条 `Error`，然后仍弹出移动对话框。不要提示 `AlreadyHere`。`moveNext` 用一个标记记录是否出现过 `Moved`。`Busy` 只弹现有 Toast，不调用 `moveNext(index + 1)`；标记为真时调用一次 `onMembershipChanged`。`Locked`、`TargetNonEmpty`、`CorruptMultiOwner`、`Error` 仍继续下一项。全部走完时的那次 `onMembershipChanged` 保持不变。
- 明确不改什么：不在 `Busy` 之后继续移动。不把失败提示改成逐条罗列。不新增字符串，复用现有 Toast 文案。不改单条冲突和多条冲突的对话框分支。
- 本地验证：应新增断言：`conflicts` 非空且 `items` 含 `Error` 或 `Busy` 或 `CorruptMultiOwner` 时仍出现对应 Toast，并且对话框仍出现；先 `Moved` 再 `Busy` 时后续包不再移动，且 `onMembershipChanged` 恰好一次；第一条就是 `Busy` 时不调用 `onMembershipChanged`。

## r12-4

- 判定：hold
- 问题类：选中的分组标签在 `groupList` 里解析不到就会被 `mapNotNull` 丢掉，过滤退化成只看内置标签，空的内置集合对每个应用都为真。
- 不变量：选了分组标签就必须按这些标签约束；解析不到的标签算未命中，不得变成“没选”；只有根本没选分组标签时才只检查内置标签。
- 要动的符号或文件：`AppTagHelper.filterAppsByTags`。保留 `groupTagIds`。解析时分开“找到的 `SnapGroup`”和“选了但 `groupList` 里没有的 id”。`groupTagIds` 为空时维持现在的内置标签判断。`groupTagIds` 非空时，结果改为内置标签全部命中，且每个选中的分组标签都命中；任一 id 解析不到则该标签未命中，`all` 为假。`groupsToCheck` 为空且 `groupTagIds` 非空时，禁止返回 `builtinMatch`。
- 明确不改什么：不改多个分组标签“必须全部命中”的合取。不改 `selectedTagIds` 为空表示不过滤。不改 `getAllAvailableTags`：芯片仍来自 `GlobalConfig.groups`，`groupList` 里没有该分组时名称仍可回退为分组 id。
- 本地验证：应新增断言：选中的分组标签全部解析不到时结果为空，而不是原列表；两个分组标签只解析到一个时结果为空；只选 Xposed、没有分组标签时仍只留下带 Xposed 标签的应用；Xposed 与一个解析不到的分组标签同时选中时结果为空。

## r12-5

- 判定：hold
- 问题类：分组标签只判断 `group.path/packageName` 是不是目录，不比较 `appInfo.userId` 和 `group.userId`，另一个用户的同包名会被留下。
- 不变量：应用算在该分组里，当且仅当 `appInfo.userId == group.userId` 且该包目录存在。
- 要动的符号或文件：`AppTagHelper.isAppInGroup`。先比较 `appInfo.userId` 与 `group.userId`，不相等直接返回 false；相等再做现在的 `fileType == TYPE_DIR` 判断。`filterAppsByTags` 继续只通过 `isAppInGroup` 判断分组，不必再写一套比较。
- 明确不改什么：不改目录存在性判断。不改 `SnapGroup.userId` 的存储。不在 `getAllAvailableTags` 里按用户删掉其它分组的芯片。不改标签 id 的 `group_` 前缀。
- 本地验证：应新增断言：目录存在但 `appInfo.userId != group.userId` 时 `isAppInGroup` 为 false；用户相同且目录存在时为 true；`filterAppsByTags` 选中另一用户的分组标签时，不留下当前用户的同包名应用。

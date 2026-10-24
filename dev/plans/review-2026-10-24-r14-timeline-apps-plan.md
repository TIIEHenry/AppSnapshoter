---
title: "r14 timeline-apps 修改方案"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "读失败的忽略列表不得写回；最近 7/30 天按查询时刻重算右端；选应用与导出按分组和用户区分；用户 Tab 与 currentUserId 对齐。"
---

# r14 timeline-apps 修改方案

## r14-1
- 判定：hold
- 问题类：忽略列表读失败被当成空列表，随后的增删会整份覆盖 `ignored_apps.json`。
- 不变量：文件不存在是合法的空列表，可以写入。文件存在但读盘或 JSON 解析失败（含 `parseArray` 得到 null）不是空列表。失败结果不得进入 `cachedIgnoredKeys`。没有一份成功读到的列表时，不得 `writeText`。失败期间 `isIgnored` 不得返回 false，`filterIgnoredApps` 不得把入参原样放行。
- 要动的符号或文件：`IgnoreAppsConfig.loadFromFile`、`getIgnoredAppKeys`、`saveIgnoredAppKeys`、`addIgnoredApp`、`removeIgnoredApp`、`isIgnored`、`filterIgnoredApps`、`clearCache`。用内部状态区分「未加载 / 成功（含空列表）/ 失败」。失败不写入缓存，下次 `getIgnoredAppKeys` 可再读。`saveIgnoredAppKeys` 在成功加载之前直接返回。`addIgnoredApp` 与 `removeIgnoredApp` 只有成功加载后才改列表并保存。`clearCache` 同时清掉失败状态。`getIgnoredAppKeys(): List<String>` 的签名保持不变。
- 明确不改什么：键格式 `packageName@userId`、`normalizeKey`、`removeIgnoredApp` 对 userId 0 的裸包名兼容删除、文件名 `ignored_apps.json`。
- 本地验证：没有现成测试。应新增断言：文件存在但读取抛错时缓存仍未建立，接着 `addIgnoredApp` / `removeIgnoredApp` 后磁盘字节不变；文件不存在时 `addIgnoredApp` 写出只含新键的列表；读失败时 `isIgnored` 不为 false，`filterIgnoredApps` 不返回未过滤的原列表；文件不存在时 `isIgnored` 为 false。

## r14-2
- 判定：hold
- 问题类：最近 7 天和最近 30 天的右端停在创建区间时的 `now`，新快照不会进入后续查询。
- 不变量：比较仍是左闭右开，`makeTime >= endTimeExclusive` 的归档不入结果。`TODAY` 的右端仍是下一个本地午夜，`YESTERDAY` 与 `CUSTOM` 仍用已保存的端点。`LAST_7_DAYS` 与 `LAST_30_DAYS` 每次真正查询时，右端用 `TimelineRepository.resolveTimeRange` 按当时的 `now` 重算。重算不得写回 `timeRange`，避免观察者再次进入 `requery` 时 `now` 已变而连环刷新。列表、热力图和批量恢复、删除、导出使用同一份有效区间。再次 `bindGroupList` 到同一 `LiveData` 时仍要查询，且不重复注册观察者。刷新区间不得调用 `setTimeRange`，以免清掉多选。
- 要动的符号或文件：`TimelineViewModel.requery`、`bindGroupList`；新增只读的有效区间函数（按 `preset` 决定是否调用 `resolveTimeRange`）。`TimelineFragment` 里把 `timeRange.value` 交给热力图、`batchRestore`、`batchDelete`、导出的几处改为读这个有效区间。
- 明确不改什么：`resolveTimeRange` 里 `TODAY` / `YESTERDAY` / `CUSTOM` 的公式，`query` 的 `until` 过滤，芯片切换时已有的 `resolveTimeRange` 调用，`GlobalConfig` 里自定义起止的持久化。
- 本地验证：已有 `left-closed right-open boundary` 保持通过。应新增断言：预设为最近 7 天或 30 天时，有效区间的右端晚于创建时冻结的 `endTimeExclusive`，冻结点之后、新右端之前的 `makeTime` 能进入 `TimelineRepository.query`；`CUSTOM` 与 `TODAY` 的端点不被这次重算改掉。

## r14-3
- 判定：hold
- 问题类：多选只记包名，确认提交的是当前列表与包名的交集。
- 不变量：选中身份是包名加 `userId`。确认结果和工具栏计数都等于这份额外保存的跨筛选集合，不随搜索或用户 Tab 缩小。看不见的已选项留在集合里。另一个用户的同包名不会显示为已选，也不会被确认出去。
- 要动的符号或文件：`SelectAppAdapter` 的 `selectedApps`、`getSelectedApps`、`selectAll`、`clearSelection`、`toggleAppSelection`、`onBindViewHolder` 的勾选判断。把包名集合改成「包名:userId → AppInfo」的映射，键与同文件成员资格索引的 `"${packageName}:${userId}"` 一致。`getSelectedApps` 返回映射中的 `AppInfo`，不再 `currentList.filter`。`SelectAppFragment.onFilteredAppsChanged` 在 `submitList` 之后用 `getSelectedApps().size` 刷新计数。
- 明确不改什么：`AppDiffCallback` 的包名加 `userId` 相等判断，单击 `onItemClick` 的单选确认，多选工具栏的显示与文案。
- 本地验证：没有现成测试。应新增断言：选中 user 0 的某包后，当前列表换成 user 10 的同包时该行未勾选，`getSelectedApps` 仍是 user 0 的 `AppInfo`；搜索滤掉已选项后计数仍是原选中数，确认列表仍包含该项；`selectAll` 只把当前可见项并入集合，不删除看不见的已选项。

## r14-4
- 判定：hold
- 问题类：批量导出的目标路径只有包名和归档名，复制未完成或与其他条目同目录时仍计入成功。
- 不变量：目标路径带上 `TimelineEntryKey` 的分组 id 和 `userId`（`TimelineRepository.query` 构造键时传入的 `group.id` 与 `app.appInfo.userId`）。同一条目的每个匹配归档都复制成功才 `succeeded.add`。循环因 `isCancelled` 而 `break`，或某次复制未成功，该条目不得进入 `succeeded`，并记入 `failed`。`copyRecursively` 的第三个参数保持 `false`。若该方法返回布尔，只有 `true` 算这次复制成功；若无返回值，则以未中断且未抛异常为准。抛错仍走现有 `failed` 分支。
- 要动的符号或文件：`TimelineBatchOperator.batchExport` 里归档循环、目标路径字符串、`result.succeeded.add`。
- 明确不改什么：`batchRestore` 与 `batchDelete` 的成功条件，`TimelineRepository.query` 的分条规则，发现稿所引两条单测的断言。
- 本地验证：已有 `cross-group same package name produces separate entries`、`userId distinguishes entries` 保持通过。应新增断言：同包名、同归档名、分组或 `userId` 不同的两条，导出目录不同；一个条目有多份归档时，在第二份复制前取消，`succeeded` 不增加且该条在 `failed`。

## r14-5
- 判定：hold
- 问题类：用户 Tab 高亮的 id 可以和列表使用的 `currentUserId` 不一致。
- 不变量：建完 Tab 后，若 `currentUserId` 在本次用户序列里，高亮该项；若不在，高亮第一项，并 `setUserFilter` 成该项的 id。监听器登记之前不得依赖 `addTab` 的默认选中。`select()` 若只走到空的 `onTabReselected`，仍然要显式 `setUserFilter` 到最终高亮项的 id。高亮 id 与 `getAppsForCurrentUser` 使用的 id 相同。
- 要动的符号或文件：`AppsViewModel.currentUserId` 改为外部可读、仅本类可写。`AppsListComponent.setupUserTabs`：`addTab(tab, false)`，按上面的规则选中，并 `setUserFilter`。`applyUserTabs` 在 id 序列相同时仍直接返回。
- 明确不改什么：`applyFilter` 的标签、成员资格和系统/用户过滤，`resetFilters` 把 `currentUserId` 置 0 的语义，目录加载与 `AppsCatalogUi` 的加载条判断。
- 本地验证：没有现成测试。应新增断言：当前 id 为 0、序列为 `[10, 0]` 时高亮 0 且列表取 user 0；当前 id 为 0、序列为 `[10, 11]` 时高亮 10 且 `setUserFilter(10)`；当前 id 为 10、序列从其他顺序改为 `[0, 10]` 时重建后仍高亮 10，列表仍取 user 10。

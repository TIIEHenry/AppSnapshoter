---
title: "r14 timeline-apps 审查发现"
type: guide
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "忽略列表读失败会覆盖原文件；最近 7/30 天漏掉新快照；导出成功数与选应用确认集、用户 Tab 都会对错对象。"
---

# r14 timeline-apps 审查发现

通读 timeline、apps、selectapp、settings、`MainActivity.kt` 以及对应单测和 androidTest 里的 `.kt` / `.java`。下面 5 条都是这次读到的活码。

## r14-1
- 严重度：高
- 症状：忽略列表文件一旦读失败，进程内会被当成「没有任何忽略项」。之后再添加或移除一条，磁盘上的原列表会被这一条（或删空后的列表）整份覆盖。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/main/settings/IgnoreAppsConfig.kt:29-49`、`app/src/main/java/tiiehenry/android/app/snapshot/main/settings/IgnoreAppsConfig.kt:56-67`、`app/src/main/java/tiiehenry/android/app/snapshot/main/settings/IgnoreAppsConfig.kt:74-93`
- 为什么是问题：`loadFromFile` 在读盘或 JSON 解析抛错时返回 `emptyList()`。`getIgnoredAppKeys` 把这个空列表放进 `cachedIgnoredKeys`，和「文件不存在」走同一条结果。`addIgnoredApp` / `removeIgnoredApp` 都是先拿这份缓存再 `saveIgnoredAppKeys`。保存前不区分这次是读失败还是真的为空，`writeText` 成功后缓存改成刚写入的内容。文件只是暂时读不出、或写到一半损坏时，下一次增删会把其余 `package@userId` 从 `ignored_apps.json` 里抹掉。读失败之后、写回之前，`isIgnored` 和 `filterIgnoredApps` 也会按空列表放行，已忽略应用重新出现在会过滤忽略项的列表里。
- 方向：读失败不要写入缓存，保存前如果没有一份成功读到的列表就中止写回。

## r14-2
- 严重度：中
- 症状：时间线停在「最近 7 天」或「最近 30 天」时，打开该范围之后新建的快照不会出现；分组列表刷新也不会把它们补进来。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/main/timeline/TimelineRepository.kt:25-26`、`app/src/main/java/tiiehenry/android/app/snapshot/main/timeline/TimelineRepository.kt:41-47`、`app/src/main/java/tiiehenry/android/app/snapshot/main/timeline/TimelineRepository.kt:64-68`、`app/src/main/java/tiiehenry/android/app/snapshot/main/timeline/TimelineViewModel.kt:55-64`、`app/src/main/java/tiiehenry/android/app/snapshot/main/timeline/TimelineViewModel.kt:78-86`、`app/src/main/java/tiiehenry/android/app/snapshot/main/timeline/TimelineViewModel.kt:129-145`、`app/src/main/java/tiiehenry/android/app/snapshot/main/timeline/TimelineFragment.kt:92`
- 为什么是问题：查询是左闭右开，`makeTime >= endTimeExclusive` 的归档被丢掉。`TODAY` 的右端是下一个本地午夜，同一天里稍后产生的快照仍能在重新查询时命中。`LAST_7_DAYS`、`LAST_30_DAYS` 和 `defaultLast7Days` 的右端是调用当时的 `now`。`loadPersistedTimeRange` 只在 ViewModel 创建时解析一次。`requery` 使用已经放在 `timeRange` 里的那个区间，分组列表更新只会用同一右端再查。`bindGroupList` 在已经绑定同一 `LiveData` 时直接返回，再次进入时间线页不会重算右端。右端之后做成的快照，若该应用在窗口内没有更早的归档，整行都不会出现。
- 方向：每次查询「最近 7/30 天」时用当前时间重算右端，或让右端覆盖到下一次本地午夜。

## r14-3
- 严重度：中
- 症状：多选确认交出去的应用，不是界面上那一次选择。换用户 Tab 或改搜索后，计数仍是旧数字；同包名的另一个用户会被当成已选，当前列表里暂时看不见的已选项则被丢掉。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/main/selectapp/SelectAppAdapter.kt:24`、`app/src/main/java/tiiehenry/android/app/snapshot/main/selectapp/SelectAppAdapter.kt:42-51`、`app/src/main/java/tiiehenry/android/app/snapshot/main/selectapp/SelectAppAdapter.kt:60-66`、`app/src/main/java/tiiehenry/android/app/snapshot/main/selectapp/SelectAppFragment.kt:147-164`、`app/src/main/java/tiiehenry/android/app/snapshot/main/selectapp/SelectAppFragment.kt:201-203`
- 为什么是问题：`DiffUtil` 用包名加 `userId` 区分行，选中集只存包名。`getSelectedApps` 再拿这个包名去滤 `currentList`。多选时筛选行仍在，用户 Tab 和搜索会换掉 `currentList`，但 `onFilteredAppsChanged` 只 `submitList`，不重算工具栏计数。确认按钮拿的是调用瞬间的 `currentList` 交集：搜索把已选项滤掉之后，计数还停在滤前的个数，确认却只提交还看得见的那些。切到另一个也安装了该包的用户时，那一行会显示为已勾选，确认提交的是这个用户的 `AppInfo`，不是刚才勾的那个用户。
- 方向：选中键使用包名加 `userId`，确认和计数都针对这份跨筛选的选中集，而不是当前一页的包名交集。

## r14-4
- 严重度：中
- 症状：时间线批量导出会把没有写完、或和其他条目落到同一目录的快照算作成功。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/main/timeline/TimelineBatchOperator.kt:318-329`、`app/src/test/java/tiiehenry/android/app/snapshot/main/timeline/TimelineRepositoryTest.kt:140-155`、`app/src/test/java/tiiehenry/android/app/snapshot/main/timeline/TimelineRepositoryTest.kt:158-171`
- 为什么是问题：目标路径只有 `包名/归档名`，没有 `groupId` 和 `userId`。同一时间范围内，不同分组的同包名、以及同一分组里不同 `userId` 的同包名，都会得到各自的时间线条目（单测断言了这两种条目同时存在）。它们的归档名相同时，`copyRecursively` 的目标目录相同，并且 `overwrite` 为 false。返回值被丢掉；循环因取消在中途 `break` 后，仍然执行 `succeeded.add`。完成提示里的成功数因此包含未复制、只复制了一部分、或与另一条互相覆盖同一目录的条目。
- 方向：目标路径带上分组和用户，只有该条目的归档都复制成功才计入成功。

## r14-5
- 严重度：中
- 症状：应用页和选择应用页上高亮的用户 Tab，可以不是列表实际使用的 `userId`。列表会是用户 0 的应用、空白，或仍停在上一个用户。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/main/apps/AppsViewModel.kt:23`、`app/src/main/java/tiiehenry/android/app/snapshot/main/apps/AppsViewModel.kt:54-56`、`app/src/main/java/tiiehenry/android/app/snapshot/main/apps/AppsViewModel.kt:127-136`、`app/src/main/java/tiiehenry/android/app/snapshot/main/apps/AppsListComponent.kt:219-224`、`app/src/main/java/tiiehenry/android/app/snapshot/main/apps/AppsListComponent.kt:236-265`
- 为什么是问题：`currentUserId` 初始为 0，列表只取 `appsMap[currentUserId]`。`setupUserTabs` 先 `removeAllTabs` 再 `addTab`，第一项会在监听器登记之前被选中。监听器加上之后又对第一项调用 `select()`，此时该项已经是选中态，只会走到空的 `onTabReselected`，不会 `setUserFilter`。因此第一项的 id 不是 0 时，高亮和列表从第一次建 Tab 就不一致。`applyUserTabs` 在两路用户 id 序列不同时会整表重建（catalog 的 key 顺序和 `appManager.users` 各走一次）。重建同样把高亮放回第一项，却留下重建前的 `currentUserId`。用户看到的是一个用户的 Tab，点进去的是另一个用户的应用。
- 方向：建完 Tab 后按当前 `currentUserId` 选中对应项；自动选中第一项时显式调用 `setUserFilter`。

---
title: "r12 group-meta 审查发现"
type: guide
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "分组加载、存档解析、添加移动结果和分组标签筛选有 5 处会给出错误结果。"
---

# r12 group-meta 审查发现

读完 `group`、`app`、`glide` 与 `group` 测试前缀下全部 `.kt`（22 个文件）。正确性问题 5 条。

## r12-1

- 严重度：中
- 症状：刷新分组失败后，已出现的应用会从列表消失，或同一次刷新和另一次加载叠在一起时，同一个包出现两行。列表一旦非空，不带重新加载的再次进入会一直用这份残缺或重复的结果。同一独占分组里同一个包有两份时，独占索引会把该包当成多主损坏，添加或移动被拒绝。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/group/SnapGroup.kt:87`、`app/src/main/java/tiiehenry/android/app/snapshot/group/SnapGroup.kt:92`、`app/src/main/java/tiiehenry/android/app/snapshot/group/SnapGroup.kt:106`、`app/src/main/java/tiiehenry/android/app/snapshot/group/SnapGroup.kt:153`、`app/src/main/java/tiiehenry/android/app/snapshot/group/GroupMembershipResolver.kt:149`、`app/src/main/java/tiiehenry/android/app/snapshot/group/GroupMembershipResolver.kt:161`
- 为什么是问题：`reload` 先在单独的 `synchronized` 里 `apps.clear()`，再进另一个 `synchronized` 做 `listDir` 和逐个 `add`。`listDir` 或循环中未捕获的 `fileType` 抛错时，列表已经是空的，或只加进了抛错之前的包。清空和填充不是同一临界区：线程 A 清空之后，若线程 B 已经把列表填满，A 再填充时 `reload == true` 不会走 `apps.isNotEmpty() && !reload` 的早退，会把同一批应用再 `add` 一遍。早退又把任意非空列表当成完整缓存。`buildExclusiveOwnerIndex` 按应用条目把分组加入列表，同一分组因重复条目出现两次时 `size >= 2`，`corruptKeys` 把该包标成损坏。
- 方向：在同一临界区里读完再整体替换列表，失败则保留旧列表；索引按分组去重后再判断多主。

## r12-2

- 严重度：中
- 症状：一个应用目录下，后面的存档会整段缺失，或某条存档的额外项全部为空，分组里这个应用仍然在，看起来像已经加载完成。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/group/ArchivedApp.kt:39`、`app/src/main/java/tiiehenry/android/app/snapshot/group/ArchivedApp.kt:74`、`app/src/main/java/tiiehenry/android/app/snapshot/group/ArchivedApp.kt:78`、`app/src/main/java/tiiehenry/android/app/snapshot/group/SnapGroup.kt:130`、`app/src/main/java/tiiehenry/android/app/snapshot/group/SnapGroup.kt:93`
- 为什么是问题：`loadArchives` 的循环里，`readDataItems`、`fileType`、`listDir` 抛错会直接跳出，已经放进 `archives` 的条目保留，后面的存档不再读。`extraItems` 把整次 `mapKeys` 包在一个 `try` 里，任一异常都换成 `emptyMap()`，然后照常构造 `ArchiveItem`。`SnapGroup.loadApps` 捕获 `loadArchives` 的异常后仍然 `apps.add`。分组列表一旦非空且 `reload == false`，直接返回，不会对这个 `ArchivedApp` 再读存档。
- 方向：单条存档失败只跳过该条；额外项保留已解析的条目；半截失败不要当成这个应用已经加载完成。

## r12-3

- 严重度：中
- 症状：同一批加入里只要有冲突，其它包的失败、占用或成员损坏都不会提示。用户确认全部移动后，若中途遇到占用，已经移走的包界面不刷新，后面的包也不再移动。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/group/AddAppsResultUi.kt:17`、`app/src/main/java/tiiehenry/android/app/snapshot/group/AddAppsResultUi.kt:42`、`app/src/main/java/tiiehenry/android/app/snapshot/group/AddAppsResultUi.kt:96`
- 为什么是问题：`conflicts` 非空时直接弹出移动对话框并 `return`，同批的 `Error`、`Busy`、`CorruptMultiOwner` 不会再看。`moveNext` 在 `MoveAppResult.Busy` 时只弹 Toast，不调用 `moveNext(index + 1)`，也不会在结束时调用 `onMembershipChanged`。前面的项若已是 `Moved`，成员关系已经变了，列表仍停在旧状态，其余冲突包留在原分组。
- 方向：冲突对话框之外仍要报出本批失败项；遇到占用就停止后续移动，但对已经移成的项回调刷新。

## r12-4

- 严重度：中
- 症状：用户选了分组标签，列表却不按分组缩小，仍显示全部应用（或只按 Xposed 筛过的全部应用）。多个分组标签里只有一部分能对上已加载分组时，对不上的那些等于没选。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/app/tag/AppTagHelper.kt:71`、`app/src/main/java/tiiehenry/android/app/snapshot/app/tag/AppTagHelper.kt:104`、`app/src/main/java/tiiehenry/android/app/snapshot/app/tag/AppTagHelper.kt:122`
- 为什么是问题：可选标签来自 `GlobalConfig.groups`，`groupList` 里找不到该分组时仍用分组 id 当芯片名字。过滤时 `mapNotNull` 把解析不到的标签丢掉。`groupsToCheck` 为空就走“只检查内置标签”的分支；没选 Xposed 时 `builtinTagIds.all` 对空集为真，每个应用都通过。
- 方向：选了分组标签但一个都解析不到时，不要退化成不按分组筛选；部分解析失败的标签按未命中处理。

## r12-5

- 严重度：中
- 症状：按分组标签筛选时，另一个用户上的同包名应用也会留在结果里。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/app/tag/AppTagHelper.kt:53`、`app/src/main/java/tiiehenry/android/app/snapshot/app/tag/AppTagHelper.kt:126`、`app/src/main/java/tiiehenry/android/app/snapshot/group/SnapGroup.kt:38`
- 为什么是问题：`isAppInGroup` 只判断 `group.path/packageName` 是不是目录，不比较 `appInfo.userId` 和 `group.userId`。分组只有一个 `userId`。标签来源又是全部 `GlobalConfig.groups`。用户 10 的应用列表里选中用户 0 的分组时，只要该分组目录里有这个包，用户 10 的同包名应用也会被留下。
- 方向：目录存在之外，还要 `appInfo.userId == group.userId` 才算在这个分组里。

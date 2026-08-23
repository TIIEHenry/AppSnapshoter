---
title: "存档 Tab 搜索"
type: system
status: implemented
updated: 2026-08-23
summary: "存档主页按分组集名、分组名、应用名/包名过滤列表；视图层投影，不改 archiveList SSOT，不写折叠状态"
---

# 存档 Tab 搜索设计文档

> 版本：v1.1 · 日期：2026-08-23 · 状态：implemented（Phase 1–3 已落地）  
> 关联：存档 Tab（`LauncherFragment`）、`archiveList` SSOT、[分组集](GROUP_SET.md)、[折展性能](group-set-expand-perf.md)、[可折叠搜索](../../guides/getting-started/ui-shell.md#可折叠搜索)

## 文档索引

| 章节 | 内容 |
|------|------|
| 1 | [背景与目标](#背景与目标) |
| 2 | [现状分析](#现状分析) |
| 3 | [已定决策](#已定决策) |
| 4 | [功能设计](#功能设计) |
| 5 | [核心业务逻辑](#核心业务逻辑) |
| 6 | [模块与文件结构](#模块与文件结构) |
| 7 | [边界与质量](#边界与质量) |
| 8 | [实施计划](#实施计划) |

## 快速摘要

存档 Tab 增加与时间线/应用 Tab **同一套**可折叠搜索。查询匹配 **分组集名、分组名、应用 label、包名**。有查询时由纯函数 `ArchiveSearchFilter` 从 `groupList` / `groupSetList` / `archiveRoots` 生成**展示列表**；`AppDataRepository.archiveList` 仍是结构 SSOT，搜索**不得**改 MMKV 折叠、不得 `reprojectArchiveListLocked`。命中折叠集时，展示层临时露出 Header + 命中卡片，清空查询后回到真实折叠。

---

## 背景与目标

### 需求背景

分组与分组集变多后，存档主页只能靠滚动或底栏长按快跳定位。时间线、应用 Tab、选应用、忽略应用已有 `layout_search_field` + `CollapsibleSearchController`；存档 Tab 没有筛选行，顶栏已是「添加 / 全部折叠 / 排序」三图标。

用户在主页的典型意图是：**找到某一块存档，或找出某个应用落在哪一组**。按快照时间/备注查找已由时间线承担。

### 目标

1. 在存档 Tab 按关键词缩小列表，定位分组集、分组、组内应用。
2. 复用现有可折叠搜索控件与高亮，交互与时间线一致。
3. 搜索是**视图层过滤**，不破坏 `archiveList` 连续块 SSOT，不把「搜到了」写成持久折叠。
4. 折叠集 / 折叠分组里的命中仍能出现；清空查询后折叠状态与搜索前一致。

### 非目标（v1）

- 搜快照文件名、备注、时间戳（时间线已覆盖）
- 持久化搜索词、搜索历史、拼音/模糊分词
- 改 `archiveList` 投影或 `loadGroupsMutex` 写路径
- 底栏快跳菜单内搜索
- 应用 Tab / 时间线行为变更

---

## 现状分析

### 列表与折叠

| 概念 | 真源 | 搜索相关约束 |
|------|------|----------------|
| 存档列表形状 | `AppDataRepository.archiveList`（mutex 内 `reprojectArchiveListLocked`） | Adapter/Fragment **禁止**自己 insert/remove 卡片。`submitList` 只吃 ViewModel 展示列表（无查询 = `archiveList`，有查询 = Filter 物化结果） |
| 顶层顺序 | `GlobalConfig.archiveRoots` | 搜索结果保持同一相对顺序，只省略未命中块 |
| 集折叠 | `SnapGroupSet.isCollapsed` → 投影不发射成员 `GroupCard` | 只看 `archiveList` **搜不到**折叠集内的组/应用 |
| 分组折叠 | `SnapGroup.isCollapsed` + 本地 `renderBody`（**不**再投影，见 [折展性能](group-set-expand-perf.md)） | 标题点击 / `scrollToPackage` 写 live getter。搜索若把 `renderBody` **全局**改成只吃 `GroupCard.collapsed`，无查询时手势会卡在过期快照 |
| 组内应用 | `SnapGroup.apps`（`loadApps` 后内存） | 与时间线相同，只搜已加载成员；加载中结果会随 `groupList` 补全 |
| 跨 Tab 跳转 | `navigateToGroup` / `navigateToGroupSet` | 消费方读 `currentList`；搜索把卡片滤掉会吞掉跳转 |

### 可复用

- `CollapsibleSearchController` + `layout_search_field` + `Widget.AppSnapshot.SearchField`
- `TimelineTextHighlight`（可抽到 `ui.widget`，避免 launch 依赖 timeline 包）
- `TimelineViewModel` 的「全量 LiveData + `searchQuery` → 展示列表」模式
- `ArchiveListProjector` 的纯函数 + 单测风格（`ArchiveListProjectorTest`）
- `GroupCard.collapsed` 字段（投影快照，DiffUtil 已禁止读 live getter）

### 不能复用的误区

| 误区 | 后果 |
|------|------|
| 为露出命中调用 `setGroupSetCollapsed(false)` / 写 `SnapGroup.isCollapsed` | 清空搜索后列表被永久展开 |
| 在 Adapter/Fragment 里按 query 自己拼卡片再 `submitList` | 与 `archiveList` observe 互踩；展示列表必须由 ViewModel 一次给出 |
| 把搜索过滤做进 `reprojectArchiveListLocked` | 结构 SSOT 与筛选态缠在一起；快跳/排序/一键折叠都会吃到筛选后的形状 |
| 第四个顶栏常驻图标再加一行「只放搜索钮」的筛选条 | 顶栏已满；单独一行只有图标，空白难看 |

---

## 已定决策

| 决策 | 结论 | 理由 |
|------|------|------|
| 搜索范围 | **分组集名 + 分组名 + 应用 label + 包名**；忽略大小写，`contains` | 主页定位存档块/应用归属；快照维交给时间线 |
| 数据路径 | **视图层** `ArchiveSearchFilter` 纯函数；`archiveList` 不因搜索改变 | 守住分组集连续块 SSOT |
| 查询状态 | `LauncherViewModel.searchQuery`（不进 MMKV） | 屏级状态；进程死亡丢失可接受 |
| 折叠 | 搜索用 ViewHolder **`displayCollapsed`** 覆盖展示；**禁止写**集/组 MMKV `isCollapsed` | 组折叠轴仍是 live getter + `renderBody`，不是 `archiveList` 投影 |
| 集名命中 | 露出该集 Header + **全部**成员卡片（成员 = `deriveMembers` 后再 `orderGroups`） | 「搜到这个集」应看到集的内容；`groupOrder` 只排序，不是成员真源 |
| 仅成员/应用命中 | 露出 Header（`expanded=true`）+ **仅命中**成员 | 避免为找一个应用展开整集噪声 |
| 组名命中 | `displayCollapsed=false`；组内网格 **全部**应用；命中项高亮 | 组名匹配表示用户要看这一组 |
| 仅应用命中 | `displayCollapsed=false`；网格 **只显示命中应用**；高亮 | 一眼看到匹配项 |
| 有查询时折展 | Header / 吸顶 / 组标题 / `menu_collapse_all` **全部**不写折叠 | sticky 与列表项共用 `GroupSetHeaderBinder`，漏 overlay 会把「搜到了」写成真实折叠 |
| UI 入口 | `menu_launcher` **最左侧**搜索图标；输入框在 Fragment 顶部展开，默认 `gone` | 不另做空筛选行。四枚 `showAsAction=always`；窄屏把「排序」降为 `ifRoom`，搜索保持 always |
| 控件 | 复用 `CollapsibleSearchController`。`menu_search` 用 `actionLayout`；`onCreateMenu` **只重绑 toggle**，禁止对同一 `searchField` 再 `new` Controller | MenuProvider 挂 `RESUMED`：进设置会拆 menu，返回叠 `doOnTextChanged` 并误 `expand()` |
| 跳转消费 | 有 pending 先 `clearSearch`；`tryConsumeNavigate` **仅当** `searchQuery.isBlank()` 且本次 `submitList` 是未过滤 `archiveList` | 现协议在 commit 后立刻 `indexOfFirst` + `value=null`，打在过滤列表上会滚偏或丢包名滚动 |
| 高亮 | Adapter 级 `searchQuery` + payload；组标题、集名、应用名都刷（对齐 `TimelineAdapter.updateSearchQuery`） | 组名持续命中时 `visiblePackages` 一直 `null`，DiffUtil 会判内容相同 |
| 网格排序 | **先**对全量 `group.apps` 走 `applySorting`，**再**按 `visiblePackages` 截断 | CUSTOM 排序会按传入列表 `removeAll` 并 `save()`；对子集排序会删掉未命中包的顺序 |
| 单卡 refresh | `refresh` 带着当前 `visiblePackages` 先截再 submit；apps 变化后 ViewModel 按当前 query 重物化 | `GroupActionsController` 只 `loadApps` + `onRefresh`，不 `postValue(groupList)` |
| 底栏快跳 | 仍读未过滤的 `archiveList` | 快跳是结构导航，不受筛选影响 |
| 空结果 | Fragment 内空文案，隐藏列表（sticky 一并隐藏） | 与「列表被滤空」一致 |
| 防抖 | **无**（对齐时间线逐字过滤） | 内存 `contains`，分组量级足够 |

### 考虑过的方案

**数据**

1. **推荐：视图层纯函数过滤** — `archiveList` 只在 query 为空时直接 `submitList`；有 query 时用 `groupList`+集+roots 生成展示列表。
2. Repository 带 query 再投影 — 实现少一层，但污染 SSOT，一键折叠/快跳/Diff 都要区分「真形状 vs 筛选形状」。
3. 只高亮不隐藏 — 分组一多仍然找不到，达不到「搜索」目标。

**入口**

1. **推荐：顶栏 MenuItem 搜索 + 内容区输入框** — 无空筛选行。
2. 内容区常驻「仅搜索图标」行 — 与时间线控件一致，但存档没有 Chip，会空出一横条。
3. 只做 `SearchView` 收进 overflow — 发现性差，且与现有折叠搜索不一致。

---

## 功能设计

### 信息架构

```
MaterialToolbar
  [搜索] [添加] [全部折叠] [排序]     ← menu_launcher；搜索最左
Fragment 内容
  layout_search_field                 ← 默认 gone；展开后推开列表
  FrameLayout
    RecyclerView                      ← 无查询：archiveList；有查询：过滤结果
    sticky SetHeader
    empty (无匹配)
```

水平间距与其它 Tab 一致：输入框左 `filter_horizontal_padding`（12dp），右 `filter_section_inset_end`（8dp）。

### 交互

| 操作 | 行为 |
|------|------|
| 点顶栏搜索 | 展开输入框、焦点、弹出键盘；图标切为关闭 |
| 点关闭 / 再点搜索图标 | 收起输入框、收键盘；**过滤词保留**；有词时搜索图标主题色（现有 Controller） |
| 输入 | 忽略大小写 `trim` 后过滤；空串 = 恢复 `archiveList` |
| 命中集 Header | 有查询时不切换真实折叠；标题高亮 |
| 命中分组标题 | 有查询时不切换真实折叠；标题高亮 |
| 命中应用 | 可点按/长按，恢复与菜单与平时相同 |
| 清空文字（endIcon） | 恢复完整 `archiveList`，输入框仍展开 |
| 时间线/应用跳到分组或集 | 清空查询并收起输入框，再滚动 |
| 配置变更 / `loadGroups` | `archiveList`/`groupList` 更新后用**当前 query** 重算展示列表 |
| 切走存档 Tab | 不强制清空（`LauncherViewModel` 活在 Activity） |

### 文案（三份 locale 同步）

| key | 默认 zh | en |
|-----|---------|-----|
| `archive_search_hint` | 搜索分组或应用… | Search groups or apps… |
| `archive_search_empty` | 无匹配的分组或应用 | No matching groups or apps |
| 开关 / 关闭 | 复用 `timeline_search_toggle` / `timeline_search_close` | 已有 |

---

## 核心业务逻辑

### 数据流

```text
archiveList ──┐
groupList ────┤
groupSetList ─┼─► LauncherViewModel.displayedArchiveList ──► GroupsAdapter.submitList
searchQuery ──┘         │
                        ├ query.isBlank() → archiveList 原样
                        └ else → ArchiveSearchFilter.filter(...) → 物化 ArchiveListItem
```

`displayedArchiveList` 用 `MediatorLiveData`（或 `groupList`/`archiveList`/`searchQuery` 各 observe 后在主线程合并）。**不要**把过滤放进 `SnapshotViewModel` 或 Repository。Fragment 观察 `searchQuery` 再收起输入框，ViewModel **不**持有 Controller。

物化在 `LauncherViewModel`：Filter 只输出 Draft（id + `collapsed` + `visiblePackages`），再用当前 `groupList` / `groupSetList` 填引用。

`membersBySetId` **必须** `ArchiveListProjector.deriveMembers` + `orderGroups`，roots 读当前 `GlobalConfig.archiveRoots`。禁止把 `groupOrder` 当成员列表（会漏未入序新目录）。`deriveMembers` 已是 public，不复制 path 规则。

### `ArchiveSearchFilter` 输入（可单测、不碰 MMKV）

```kotlin
data class SearchableApp(val packageName: String, val label: String)
data class SearchableGroup(val id: String, val name: String, val path: String, val apps: List<SearchableApp>)
data class SearchableSet(val id: String, val name: String, val groupOrder: List<String>)

data class Input(
    val query: String,                          // 调用方已 trim；空串不应进入
    val roots: List<ArchiveRoot>,
    val setsById: Map<String, SearchableSet>,
    val groupsById: Map<String, SearchableGroup>,
    val membersBySetId: Map<String, List<SearchableGroup>>,
)

sealed class DraftItem { /* SetHeader / GroupCard(visiblePackages) / EmptySetHint */ }
fun filter(input: Input): List<DraftItem>
```

成员排序复用 `ArchiveListProjector.orderGroups`（用 `GroupSnap(id, path)` 适配），禁止复制一份 basename 规则。

### 匹配

```text
q = query.trim()
blank(q)                 → 不进入 Filter，直接 archiveList
set.name.contains(q, i)  → 集名命中
group.name.contains(q, i)→ 组名命中
app.label / packageName  → 应用命中（synchronized(group.apps) 读）
```

不搜路径、id、快照名。`apps` 尚未加载时，该组只能靠组名命中。

### 过滤算法（保持 `archiveRoots` 顺序）

对每个 `root`：

**`ArchiveRoot.Set`**

1. 按现有 `groupOrder` + path 派生得到成员序列（与 `ArchiveListProjector.orderGroups` 相同）。
2. 集名命中 → 发射 `SetHeader(expanded=true, groupCount=成员数)`；无成员则再发射 `EmptySetHint`；有成员则按序发射**全部** `GroupCard`：
   - `collapsed = false`
   - `visiblePackages = null`（网格全量）
3. 集名未命中、但存在「组名或应用命中」的成员 → 发射 Header（`expanded=true`，`groupCount=命中成员数`）+ **仅这些** `GroupCard`：
   - `collapsed = false`
   - 组名命中：`visiblePackages = null`
   - 仅应用命中：`visiblePackages = 命中包名`
4. 否则跳过该集（不发射 Header）。

**`ArchiveRoot.Group`（独立分组）**

- 组名或应用命中 → 发射 `GroupCard(setId=null, collapsed=false, visiblePackages=同上)`。
- 已属于某集的 `g:` 仍按投影规则跳过（与 `ArchiveListProjector` 一致）。

连续块不变量：**同一 `setId` 的 `GroupCard` 必须紧跟其 `SetHeader`**。Filter 输出须能通过与 `assertContiguousBlocks` 等价的检查。

### `ArchiveListItem` 增补

```kotlin
data class GroupCard(
    val group: SnapGroup,
    val setId: String?,
    val accentColor: Int? = null,
    val collapsed: Boolean,
    /** null = 组内全部应用；非 null = 网格只显示这些包名 */
    val visiblePackages: Set<String>? = null,
)
```

`materializeArchiveList` 继续只填 `visiblePackages = null`。DiffUtil：`visiblePackages` 变化视为内容变化。组名命中 + `apps` 从空变有时同 `SnapGroup` 实例可能让 DiffUtil 跳过——物化带 **apps 包名指纹**（或 `groupList` 更新后强制 `refresh` 可见卡片）。

### 展示折叠：`displayCollapsed`（禁止全局改 bind 只吃投影）

组折叠轴不变：无查询时标题点击仍写 `group.isCollapsed` 再 `updateCollapseState`（[折展性能](group-set-expand-perf.md)：「组级折叠不要改成再投影」）。

ViewHolder 另持 `displayCollapsed`：

| 时机 | 行为 |
|------|------|
| `bind` | 有查询：`false`（露出命中）。无查询：`group.isCollapsed` |
| 无查询标题点击 / `scrollToPackage` | 写 MMKV + 改 `displayCollapsed` + `renderBody(displayCollapsed)` |
| 有查询标题点击 | **不写** MMKV、不改 `displayCollapsed` |
| `renderBody` | 空组优先；否则看 **`displayCollapsed`**，不直接读 live getter 决定 UI |

`GroupCard.collapsed` 仍给 DiffUtil 用；搜索物化时填 `false`。它不是 `renderBody` 的唯一真源。

### 网格与高亮

`refresh()`：`sorted = applySorting(全量 group.apps)` → 若 `visiblePackages != null` 再按包名截断 → `submitList`。**禁止**对子集调用 `applySorting`（CUSTOM 会改 MMKV `sortOrder`）。

Adapter 级 `searchQuery` + highlight payload，覆盖：组标题、`SetHeader` / 吸顶标题、`GroupItemAdapter` 应用名。仅 query 变化、`visiblePackages == null` 时也必须 payload，不能只靠 DiffUtil。

### Header / 吸顶 / 全部折叠

`GroupSetHeaderBinder.bind(..., collapseEnabled: Boolean)`。列表项与 `GroupSetStickyHeader.show` **同一参数**。`collapseEnabled == false`（有查询）时点击不调用 `setGroupSetCollapsed`。

`menu_collapse_all`：有查询时 no-op（或先清查询再折）。禁止搜索中走 `collapseAllArchive()`。

### 跳转与 sticky

```text
navigateToGroup / navigateToGroupSet 到达
  → 若 searchQuery 非空：clearSearch()（只改 LiveData）
  → 不要在这一拍 tryConsumeNavigate
  → displayedArchiveList 因 query 变空而提交未过滤 archiveList
  → submitList commit 后：仅当 searchQuery.isBlank() 才 tryConsumeNavigate
```

`clearSearch()` 不是同步 `submitList`。过滤态第一次 `indexOfFirst` 命中后立刻 `value = null` 会滚偏或丢掉 `pendingPackage`。

`GroupSetStickyHeader` 继续看 `currentList`。空结果时 sticky `gone`。

---

## 模块与文件结构

| 文件 | 操作 | 说明 |
|------|------|------|
| `repository/ArchiveSearchFilter.kt` | 新增 | 纯函数：snaps + query → Draft（不碰 MMKV / LiveData） |
| `test/.../ArchiveSearchFilterTest.kt` | 新增 | 见[测试范围](#测试范围) |
| `main/launch/LauncherViewModel.kt` | 修改 | `searchQuery`、`displayedArchiveList`、`clearSearch()`；组装时 `deriveMembers` |
| `main/launch/LauncherFragment.kt` | 修改 | 观察展示列表；Menu 只重绑 toggle；空态；pending 时先清 query，commit 完整列表后再 consume |
| `res/layout/fragment_launcher.xml` | 修改 | 顶栏下搜索框 + 空文案 |
| `res/menu/menu_launcher.xml` | 修改 | 最左 `menu_search` + `actionLayout` |
| `main/launch/ArchiveListItem.kt` | 修改 | `GroupCard.visiblePackages` |
| `main/launch/GroupsAdapter.kt` | 修改 | `displayCollapsed`；全量排序后再截 `visiblePackages`；highlight payload |
| `main/launch/GroupActionsController.kt` | 修改 | 有查询不写 `isCollapsed`；`onRefresh` 触发 ViewModel 重物化 |
| `main/launch/GroupItemAdapter.kt` | 修改 | 应用名高亮 |
| `main/launch/groupset/GroupSetHeaderBinder.kt` | 修改 | `collapseEnabled`；集名高亮 |
| `main/launch/groupset/GroupSetStickyHeader.kt` | 修改 | bind 传入同一 `collapseEnabled` |
| `ui/widget/TextHighlight.kt` | 新增 | 从 `TimelineTextHighlight` 抽出 |
| `main/timeline/TimelineTextHighlight.kt` | 修改 | typealias / 转调，避免双份算法 |
| `res/layout/action_archive_search.xml` | 新增 | `menu_search` 的 `actionLayout`：`FilterToolbarIcon` `ImageButton` |
| `ui/widget/CollapsibleSearchController.kt` | 修改 | 增加 `rebindToggle(ImageView)`；`expanded` 以用户为准，query 非空不强迫 `expand()` |
| `res/values{,-zh-rCN,-en}/strings.xml` | 修改 | `archive_search_hint` / `archive_search_empty` |
| `docs/guides/getting-started/ui-shell.md` | 落地后 | 存档 Tab 也列入可折叠搜索 |
| `docs/systems/snapshot/GROUP_SET.md` | 落地后 | 写死：`archiveList` 只表示结构；筛选形状只经 `displayedArchiveList` |
| `docs/systems/snapshot/group-set-expand-perf.md` | 落地后 | 组级折叠仍走 `displayCollapsed`/`renderBody`，搜索不得改成再投影 |
| `ArchiveListItem` KDoc | 落地后 | 列表结构形状来自 repository；展示形状可来自搜索 Filter |

**不改**：`AppDataRepository.reprojectArchiveListLocked`、`ArchiveListProjector` 算法、`GroupSetJumpPopup`、时间线 query 语义。`deriveMembers` / `orderGroups` 只复用。

---

## 边界与质量

### 边界

| 情况 | 行为 |
|------|------|
| 空白 / 仅空格 | 等同无查询 |
| 无命中 | 空文案；RV + sticky 隐藏 |
| 折叠集内唯一命中 | 展示层 Header + 一张卡片；MMKV 仍为折叠 |
| 空集且集名命中 | Header + `EmptySetHint` |
| `apps` 未加载 | 只按名称命中；`groupList` / 单卡 `onRefresh` 后按当前 query 重物化 |
| 进设置再返回 | Menu 重建只 `rebindToggle`；输入框展开态与过滤词保持 |
| 搜索中点「全部折叠」 | no-op（或先清查询） |
| 分组排序模式中输入搜索 | 退出排序态再提交过滤列表（避免半截 drag） |
| 批量恢复进行中 | `isBatchRunning` 仍按现逻辑 disable 操作，与搜索正交 |
| 进程重建 | 查询丢失，列表回到 `archiveList` |

### 性能

- 过滤在主线程同步即可（与时间线 `applyFilter` 同级）。若日后分组极多，再搬到 `Dispatchers.Default`，v1 不做。
- 不扫磁盘、不进 `loadGroupsMutex`。
- DiffUtil 已有；避免 `notifyDataSetChanged`。高亮用 payload。

### 测试范围

`ArchiveSearchFilterTest`（JUnit，不碰 Android / MMKV），至少：

1. 空 query 不由 Filter 负责（调用方短路）；非空但无命中 → 空列表。
2. 独立分组：组名命中；仅包名命中；仅 label 命中。
3. 折叠集 + 集名命中 → Header + **全部**成员（即使 `SetSnap.isCollapsed=true`）。
4. 折叠集 + 仅一组成员/应用命中 → Header + **一张**卡片；其它成员不出现。
5. 展开集但无任何命中 → 整块消失。
6. 连续块：有集输出时 `GroupCard.setId` 紧跟对应 Header。
7. 已入集的独立 `g:` 不重复发射。
8. 组名命中 → `visiblePackages == null`；仅应用命中 → 包名集合正确。
9. 大小写：`WeChat` 命中 `wechat`。
10. `membersBySetId` 含未出现在 `groupOrder` 里的新成员时，集名命中仍发出该组。

落地后手测 / 补测：

- 折叠集搜应用 → 清空后集仍折叠；组自定义排序不被子集搜索改掉。
- 时间线/应用跳转带包名；**本 Tab 搜索中**快跳或 `navigateToGroup` 仍滚到目标。
- 设置往返后搜索图标与过滤词；搜索中点吸顶 Header / 全部折叠不改 MMKV。

### 验收标准

- [ ] 存档顶栏可展开/收起搜索；有词时图标为主题色
- [ ] 能按集名、组名、应用名、包名过滤
- [ ] 折叠集内命中可见，且不把 `isCollapsed` 写成展开
- [ ] 清空查询后列表形状与折叠与搜索前一致
- [ ] 无命中显示 `archive_search_empty`
- [ ] 时间线/应用跳转仍能滚到分组或集（含搜索进行中触发）
- [ ] 底栏长按快跳仍按未过滤结构工作
- [ ] 搜索中点吸顶 Header / 全部折叠不改变清空后的折叠
- [ ] 自定义排序组被应用搜索过滤后，清空搜索顺序不变
- [ ] 进设置返回不出现双监听 / 输入框被强迫展开
- [ ] `./gradlew test` 含 `ArchiveSearchFilterTest` 通过
- [ ] 文案三份 locale 齐全

---

## 实施计划

### Phase 1 — 纯函数与单测

- [x] `ArchiveSearchFilter` + `ArchiveSearchFilterTest`（先红后绿）
- [x] `GroupCard.visiblePackages`；`materializeArchiveList` 默认 `null`

### Phase 2 — 展示与 Adapter

- [x] ViewHolder `displayCollapsed`；无查询手势仍写 MMKV；`scrollToPackage` 走同一字段
- [x] 全量 `applySorting` 后再截 `visiblePackages`
- [x] Adapter 级 `searchQuery` payload（组标题 / Header / 应用名）
- [x] `GroupSetHeaderBinder.collapseEnabled`；sticky 传入同一值
- [x] `CollapsibleSearchController.rebindToggle`

### Phase 3 — 接入存档 Tab

- [x] `LauncherViewModel`：`deriveMembers` + `displayedArchiveList`
- [x] `fragment_launcher` + `menu_search`；`onCreateMenu` 只重绑 toggle
- [x] 空态；pending navigate 只在空白 query 的完整列表 commit 后 consume
- [x] 有查询时 `menu_collapse_all` no-op
- [x] 同步 GROUP_SET / 折展性能 / ui-shell / 本页状态改为 implemented

Phase 1 可单独合入。**不要**把 Phase 2 做成「全局 bind 只吃 `GroupCard.collapsed`」——那会弄坏现网组折展。

---

## 审查记录

| 轮次 | 结论 | 并入 |
|------|------|------|
| Grok 子 agent 2026-08-23 | Approve with changes | v1.1：`displayCollapsed`、排序后再截断、跳转消费门闩、sticky `collapseEnabled`、`deriveMembers`、Menu 重绑、搜索中禁用全部折叠、标题 highlight payload、单卡 refresh 重物化 |

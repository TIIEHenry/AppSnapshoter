# 功能设计（UI / 交互）

[← 返回索引](../TIMELINE_FEATURE.md)

---

## 3.1 信息架构

```
┌─────────────────────────────────────────────────┐
│                  时间线 Tab                      │
├─────────────────────────────────────────────────┤
│  filter_header (colorSurface)                    │
│  ┌───────────────────────────────────────────┐  │
│  │ [今天][昨天][7天][30天][自定义]  …  [🔍]   │  │  ← Chip 行 + 搜索图标（同排）
│  │ [搜索应用或分组…              ✕]          │  │  ← 点击图标展开（默认隐藏）
│  │ ■■■□□ ■■□□□ …（热力图）                  │  │
│  └───────────────────────────────────────────┘  │
│  ─── outline_variant 分隔线 ───                 │
├─────────────────────────────────────────────────┤
│  ☐ 微信          分组A    3个快照 03/08~03/11    │
│  ☐ 支付宝        分组B    1个快照 03/10          │  ← 应用列表（日期粘性头）
│  ☐ ...                                          │
├─────────────────────────────────────────────────┤
│  [已选 N 项]  [全选] [取消] [恢复] [删除]          │  ← 多选操作栏
└─────────────────────────────────────────────────┘
```

### 3.1.1 筛选区（filter_header）

| 区域 | 实现 | 说明 |
|------|------|------|
| 时间 Chip | `ChipGroup` + `HorizontalScrollView` | 与搜索按钮同一行；Chip 可横滑 |
| 搜索入口 | `btn_search_toggle`（44dp IconButton） | 默认仅图标；有过滤词且收起时图标变主题色 |
| 搜索输入 | `layout_search_field` + `CollapsibleSearchController` | 展开后显示 Dense Outlined 输入框（14sp）；`endIcon` 清除文字；行内 ✕ 关闭面板 |
| 热力图 | `TimelineHeatmapView` | 点击某天 → 切到自定义单日范围 |
| 底部分隔 | 1dp `outline_variant` | 与列表区视觉分离 |

搜索交互由 `CollapsibleSearchController` 统一驱动（180ms `AutoTransition`），与「应用」Tab、`SelectAppFragment`、`IgnoreAppsFragment` 一致。

## 3.2 列表展示粒度

**以「应用 + 分组」为一条列表项**，唯一标识：

```
entryId = "${groupId}:${packageName}:${userId}"
```

**列表项展示字段：**

| 字段 | 来源 |
|------|------|
| 应用图标 / 名称 | `ArchivedApp.appInfo`（展示用缓存） |
| 所属分组 | `SnapGroup.name`（展示用缓存） |
| 快照数量 | 时间区域内匹配的快照数 |
| 时间摘要 | 如「3 个快照 · 03/08 ~ 03/11」 |
| 多选 checkbox | 多选模式下显示 |

**默认排序：** 按该条目在时间区域内**最新快照**的 `makeTime` 降序。

## 3.3 时间区域筛选

使用 `java.time` + `ZoneId.systemDefault()`，避免手写 `23:59:59` 边界误差。

**筛选条件（左闭右开，推荐）：**

```
startTime <= archive.metaInfo.makeTime < endTimeExclusive
```

**快捷选项：**

| 选项 | 计算方式 |
|------|----------|
| 今天 | `LocalDate.now()` 00:00 ~ 次日 00:00 |
| 昨天 | 昨天 00:00 ~ 今天 00:00 |
| 最近 7 天 | `Instant.now() - 7d` ~ `Instant.now()`（滚动窗口） |
| 最近 30 天 | `Instant.now() - 30d` ~ `Instant.now()` |
| 自定义 | Material DateRangePicker，起止日均含整天 |

**默认：** 最近 7 天

**行为：**

- 筛选变更 → 重新 query → 刷新列表 → **清空多选并退出多选模式**
- Phase 2：上次筛选 preset 存入 MMKV（`GlobalConfig` 同级独立 key）

## 3.4 搜索

| 项 | 说明 |
|----|------|
| 入口 | 筛选 Chip 行右侧 `Widget.AppSnapshot.IconButton`（`filter_icon_button_size` 44dp，图标 24dp） |
| 输入框 | 共享布局 `layout_search_field.xml`，样式 `Widget.AppSnapshot.SearchField` |
| 过滤逻辑 | `TimelineViewModel.searchQuery` → `TimelineAdapter` + `TimelineTextHighlight` |
| 持久化 | 搜索词不持久化；收起后过滤条件保留，图标高亮提示 |

## 3.5 多选交互

参考 `SelectAppFragment` + `SelectAppAdapter` 模式：

| 操作 | 行为 |
|------|------|
| 进入多选 | 长按列表项，或顶部菜单「多选」 |
| 勾选 / 取消 | 点击列表项或 checkbox |
| 全选 | 选中当前筛选结果全部条目 |
| 取消 | 退出多选，清空选中 |
| 恢复 | 触发批量恢复流程 |
| 删除 | 触发批量删除确认 |

**操作栏布局：**

- `<include layout="@layout/layout_multi_select_toolbar" />`
- `confirm_button` 设为 `gone`
- 同级追加 `restore_button`、`delete_button`（与 SelectApp 工具栏视觉一致）

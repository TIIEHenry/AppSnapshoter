---
title: "主界面壳层"
type: guide
status: active
updated: 2026-06-18
summary: "MainActivity / SettingsActivity 顶栏、底栏、竖屏锁定、可折叠搜索与内容区布局说明"
---

# 主界面壳层

> 布局文件：`app/src/main/res/layout/activity_main.xml`  
> 逻辑入口：`MainActivity.kt`

## 结构

```
ConstraintLayout (@id/coordinator)     ← FloatingBottomNav 毛玻璃采样根视图
├── toolbar_header
│   ├── MaterialToolbar (48dp)
│   └── toolbar_divider (1dp)
├── FragmentContainerView              ← NavHost，顶部约束到 toolbar_header 底部
└── BlurView (bottom_navigation_container)   固定宽 176dp，水平居中
    └── LinearLayout (@id/bottom_navigation)
        ├── ImageButton bottom_nav_archive
        ├── ImageButton bottom_nav_timeline
        └── ImageButton bottom_nav_apps
```

## 屏幕方向

| 项 | 说明 |
|----|------|
| 策略 | **仅竖屏** — `MainActivity`、`SettingsActivity` 在 `AndroidManifest.xml` 中声明 `android:screenOrientation="portrait"` |
| 原因 | 主界面为手机竖屏布局；横竖屏切换会触发 Activity 重建，导致普通 `AlertDialog`、进度框等 UI 状态丢失 |
| 扩展 | 若未来支持平板横屏，应改为 `DialogFragment` + ViewModel 保存状态，而非仅依赖 `configChanges` |

## 顶栏

| 项 | 说明 |
|----|------|
| 高度 | `@dimen/toolbar_height`（48dp）+ 状态栏 inset（`toolbar_header` paddingTop） |
| 标题 | Navigation `setupWithNavController`，随 Tab 显示「AppSnapshot / 时间线 / 应用」 |
| 菜单 | `menu_main.xml` — 右上角设置 |
| 行为 | **固定**，列表滚动不收起、无大标题 |

样式：`Widget.AppSnapshot.Toolbar` + `TextAppearance.AppSnapshot.ToolbarTitle`（17sp TitleMedium）。

## 底栏

| 项 | 说明 |
|----|------|
| 容器 | `eightbitlab.com.blurview.BlurView`，圆角胶囊悬浮于内容之上 |
| 宽度 | `@dimen/floating_nav_total_width`（176dp = 3×52dp item + 2×10dp 内边距） |
| Tab UI | 3 个等宽 `ImageButton`（`design_bottom_navigation_item_max_width` 52dp） |
| 图标 | `@dimen/floating_nav_icon_size`（26dp），由 `floating_nav_icon_padding_*` 居中 |
| 导航 | `MainActivity.setupBottomNavigation()` 手动绑定 `NavController`（`launchSingleTop` + `restoreState`） |
| 跨 Tab 跳转 | `MainActivity.selectBottomNavTab(destinationId)`（如时间线 → 存档） |
| 毛玻璃 | `FloatingBottomNav.setup(activity, container)`，采样根为 `@id/coordinator` |
| Tab 顺序 | `存档 \| 时间线 \| 应用` |
| 避让 | 列表调用 `MainActivity.floatingNavContentPaddingBottom()` 增加 `paddingBottom` |

> **为何不用 `BottomNavigationView`**：Material 底栏在横屏切换 Tab 时会 re-layout 并居中 item，导致第一个图标前出现空白；自定义 `LinearLayout` 可保持等宽左对齐。

## 各 Tab 内容区

| Tab | 顶栏下方内容 |
|-----|----------------|
| 存档 | `LauncherFragment` — 分组 RecyclerView |
| 时间线 | `TimelineFragment` — 筛选 Chip、可折叠搜索、热力图、列表 |
| 应用 | `AppsFragment` — `apps_filter_header`：用户 Tab + 系统/用户图标筛选 + 搜索（`layout_apps_filter_row`，32dp 图标）；下方 Tag 行（单行滚动 + 展开/收起） |
| 选应用 / 忽略应用 | `SelectAppFragment`、`IgnoreAppsFragment` — 同上筛选/搜索模式 |

### 可折叠搜索

时间线、应用 Tab 及选应用/忽略应用 BottomSheet 共用一套搜索 UI：

| 项 | 说明 |
|----|------|
| 触发控件 | 筛选行右侧 `AppCompatImageButton`（`Widget.AppSnapshot.FilterRowIcon` 32dp / `FilterToolbarIcon` 44dp）；`scaleType=fitCenter` + `filter_row_icon_inset` 保证图标居中 |
| 默认 | 仅显示搜索图标 |
| 展开 | 下方显示 `layout_search_field`（Dense Outlined，14sp），自动弹出键盘 |
| 收起 | 点击行内 ✕；过滤词保留，图标在有过滤时变主题色 |
| 动画 | `CollapsibleSearchController`（`ImageView`）+ `AutoTransition`（180ms）；应用 Tab 动画容器为 `apps_filter_header` |
| 样式 | `Widget.AppSnapshot.SearchField` |
| 水平间距 | 左 `@dimen/filter_horizontal_padding`（12dp）；右 `@dimen/filter_section_inset_end`（8dp） |

实现：`CollapsibleSearchController`；应用列表通过 `AppsListComponent` 回调 `getSearchFieldBinding` / `getSearchToggle` / `getSearchTransitionHost` 接入。

### 应用 Tab 筛选区（`apps_filter_header`）

```
LinearLayout (@id/apps_filter_header)     ← 左 12dp / 右 8dp padding
├── include layout_apps_filter_row        ← 单行
│   ├── TabLayout user_tab_layout       ← 用户切换（Tab.Inline，tab 左 8dp padding）
│   └── filter_icon_group               ← 系统 / 用户 / 搜索图标（各 32dp）
├── include layout_search_field           ← 可折叠（默认 gone）
└── TagsFilterLayout                    ← 与筛选区间隔 filter_row_section_gap（8dp）
```

| 项 | 说明 |
|----|------|
| 系统/用户筛选 | `AppFilterHelper.setupFilterIconToggles`；`FilterRowIcon.Toggle`；可多选，至少保留一项 |
| 图标 | `ic_filter_system` / `ic_filter_user` / `ic_search`；选中态背景 `bg_filter_row_icon_toggle` |
| 用户 Tab | `Widget.AppSnapshot.TabLayout.Inline`；`filter_tab_start_padding` 8dp、`filter_tab_end_padding` 12dp |

布局：`fragment_apps.xml`、`layout_apps_filter_row.xml`；逻辑：`AppsListComponent` + `AppFilterHelper`。

### 标签筛选（应用 Tab / 选应用）

分组名与 Xposed 等标签由 `TagsFilterLayout` 渲染，位于筛选区第二行（与展开按钮**同一行**）：

| 项 | 说明 |
|----|------|
| 样式 | `Widget.AppSnapshot.Chip.Tag`（`chipMinHeight` 22dp、11sp） |
| 筛选 Chip | `Widget.AppSnapshot.Chip.Filter`（32dp 高、13sp）— 时间线预设等 |
| 布局 | 左侧 `HorizontalScrollView` + `ChipGroup`（默认单行）；右侧 `FilterRowIcon` 展开/收起 |
| 展开 | Chip 换行占满左侧，按钮仍固定于行右；`ic_chevron_down` / `ic_chevron_up` |
| 展开按钮 | 标签数 > 1 时显示；仅 1 个标签时始终单行且不显示按钮 |
| 选中 | 多选；`ChipGroup` 要求每个 Chip 有唯一 `id`，由 `TagsFilterLayout` 在代码中分配 |
| 数据 | `AppTagHelper.getAllAvailableTags()` 从 `SnapshotViewModel.groupList` 取分组名，回退 `groupId` → `AppsViewModel.setSelectedTags()` |
| 实现注意 | reparent `ChipGroup` 时须使用 `FrameLayout.LayoutParams`（`HorizontalScrollView.measureChildWithMargins` 要求 `MarginLayoutParams`） |

布局：`layout_tags_filter.xml`；逻辑：`ui/widget/TagsFilterLayout.kt` + `AppsListComponent.setupTagsFilter()`。

## 设置页壳层

> 布局文件：`app/src/main/res/layout/activity_settings.xml`  
> 逻辑入口：`SettingsActivity.kt`（由主界面 `menu_main` → 设置打开）

```
LinearLayout (@id/settings_root)
├── LinearLayout toolbar_header
│   ├── MaterialToolbar (48dp) — 标题「设置」
│   └── toolbar_divider (1dp)
└── RecyclerView — 设置项列表（忽略应用、关于等）
```

| 项 | 说明 |
|----|------|
| 顶栏 | 与主界面同款 `MaterialToolbar`（48dp）+ 分隔线；`setSupportActionBar` + `setDisplayHomeAsUpEnabled` 返回主界面 |
| Insets | `toolbar_header` 处理状态栏；`settings_root` 处理导航栏 bottom padding |
| 子页 | `IgnoreAppsFragment`、`AboutFragment` 以 BottomSheet 展示 |

## 相关文件

| 文件 | 职责 |
|------|------|
| `values/themes.xml` | Fluent 2 色板与 Material3 组件样式（含 `Chip.Filter` / `Chip.Tag`） |
| `values/dimens.xml` | `toolbar_height`、`filter_*`（含 `filter_row_*`、`filter_tab_*`、`filter_section_inset_end`）、`tag_chip_*`、`floating_nav_*` |
| `layout/layout_apps_filter_row.xml` | 应用 Tab 用户 Tab + 图标筛选 + 搜索（单行） |
| `layout/layout_tags_filter.xml` | 应用 Tab 标签 Chip 行 + 展开按钮 |
| `layout/layout_search_field.xml` | 共享搜索输入布局 |
| `layout/activity_settings.xml` | 设置页顶栏 + 列表 |
| `main/settings/SettingsActivity.kt` | 设置页 Activity |
| `AndroidManifest.xml` | Activity `screenOrientation="portrait"` |
| `ui/widget/FloatingBottomNav.kt` | 底栏毛玻璃 |
| `ui/widget/CollapsibleSearchController.kt` | 可折叠搜索 |
| `ui/widget/TagsFilterLayout.kt` | 应用 Tab 标签多选筛选 |
| `ui/widget/SearchFieldExt.kt` | 搜索框文本监听 |

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
| 应用 | `AppsFragment` — 用户 Tab、筛选 Chip、可折叠搜索、标签、列表 |
| 选应用 / 忽略应用 | `SelectAppFragment`、`IgnoreAppsFragment` — 同上搜索模式 |

### 可折叠搜索

时间线、应用 Tab 及选应用/忽略应用 BottomSheet 共用一套搜索 UI：

| 项 | 说明 |
|----|------|
| 默认 | 筛选 Chip 行右侧仅显示搜索图标（44dp 触控区，24dp 图标） |
| 展开 | 下方显示 `layout_search_field`（Dense Outlined，14sp），自动弹出键盘 |
| 收起 | 点击行内 ✕；过滤词保留，图标在有过滤时变主题色 |
| 动画 | `CollapsibleSearchController` + `AutoTransition`（180ms） |
| 样式 | `Widget.AppSnapshot.SearchField` / `Widget.AppSnapshot.IconButton` |
| 间距 | `@dimen/filter_horizontal_padding`（12dp）统一水平 inset |

实现：`CollapsibleSearchController`；应用列表通过 `AppsListComponent` 回调 `getSearchFieldBinding` / `getSearchToggle` / `getSearchTransitionHost` 接入。

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
| `values/themes.xml` | Fluent 2 色板与 Material3 组件样式 |
| `values/dimens.xml` | `toolbar_height`、`filter_*`、`floating_nav_*` 尺寸 |
| `layout/layout_search_field.xml` | 共享搜索输入布局 |
| `layout/activity_settings.xml` | 设置页顶栏 + 列表 |
| `main/settings/SettingsActivity.kt` | 设置页 Activity |
| `AndroidManifest.xml` | Activity `screenOrientation="portrait"` |
| `ui/widget/FloatingBottomNav.kt` | 底栏毛玻璃 |
| `ui/widget/CollapsibleSearchController.kt` | 可折叠搜索 |
| `ui/widget/SearchFieldExt.kt` | 搜索框文本监听 |

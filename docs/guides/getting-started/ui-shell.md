---
title: "主界面壳层"
type: guide
status: active
updated: 2026-06-17
summary: "MainActivity 顶栏、底栏、可折叠搜索与内容区布局说明"
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
└── BlurView (bottom_navigation_container)
    └── BottomNavigationView (仅图标，无文字)
```

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
| 初始化 | `FloatingBottomNav.setup(activity, bottomNavigationContainer)` |
| Tab 顺序 | `存档 \| 时间线 \| 应用`（`bottom_nav_menu.xml`） |
| 避让 | 列表调用 `MainActivity.floatingNavContentPaddingBottom()` 增加 `paddingBottom` |

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

## 相关文件

| 文件 | 职责 |
|------|------|
| `values/themes.xml` | Fluent 2 色板与 Material3 组件样式 |
| `values/dimens.xml` | `toolbar_height`、`filter_*`、`floating_nav_*` 尺寸 |
| `layout/layout_search_field.xml` | 共享搜索输入布局 |
| `ui/widget/FloatingBottomNav.kt` | 底栏毛玻璃 |
| `ui/widget/CollapsibleSearchController.kt` | 可折叠搜索 |
| `ui/widget/SearchFieldExt.kt` | 搜索框文本监听 |

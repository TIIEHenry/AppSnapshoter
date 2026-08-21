---
title: "app 模块"
type: module
status: active
updated: 2026-08-21
summary: "UI 层 — 87 个 Kotlin 文件，24 个包，含 Activities、Fragments、ViewModels、配置管理；Fluent 2 主界面壳层；中英双语 i18n"
---

# app 模块

> 源码路径：`app/src/main/java/tiiehenry/android/app/snapshot/`

## 概述

主应用模块，包含全部 UI 逻辑、ViewModel、配置管理和业务编排。87 个 Kotlin 文件，24 个包。

## 包结构与关键类

### 根包 `tiiehenry.android.app.snapshot`
| 类 | 职责 |
|---|------|
| `SnapshotApp` | Application 入口，初始化 MMKV → ProvidersImpl → Root 检查 → 绑定服务 |
| `SnapshotViewModel` | 全局单例 ViewModel；`groupList` / `appsList` / `isAppsLoading` 来自 `AppDataRepository` LiveData；数据变更委托 repository 进程级协程（不用 `viewModelScope`） |
| `SingletonViewModelFactory` | 将 `SnapshotApp` 预创建的 `SnapshotViewModel` 注入 `activityViewModels()` |

### `app` — 应用模型与过滤
| 类 | 职责 |
|---|------|
| `AppInfo` | 应用信息数据类（包名、图标、标签、版本） |
| `AppFilterHelper` | 多维过滤辅助（`setupFilterIconToggles` 系统/用户图标、`setupFilterChips` 文字 Chip、标签、用户 ID） |
| `AppFilterType` | 过滤类型枚举 |
| `AppPermission` | 应用权限数据类 |
| `tag/AppTag`, `AppTagHelper`, `TagType` | 标签分类系统；`getAllAvailableTags()` 从 `groupList` 取分组名 |

### `archive` — 存档模型与操作
| 包 | 关键类 | 职责 |
|---|--------|------|
| `archive` | `ArchiveItem`, `MetaInfoHelper` | 存档数据类、元数据读写 |
| `archive.bean` | `MetaDataItem`, `MetaInfo` | 存档元数据结构 |
| `archive.make` | `ArchiveMaker`, `SnapshotTasks` | 快照创建核心逻辑、异步任务 |
| `archive.manage` | `ArchiveManager`, `RetentionPolicyExecutor` | 存档管理、保留策略 |
| `archive.restore` | `ArchiveRestorer`, `ApkInstaller`, `DataRestorer`, `PermissionRestorer` | 恢复流程（APK 安装 → 数据恢复 → 权限修复） |

### `config` — 配置管理
| 类 | 职责 |
|---|------|
| `GlobalConfig` | Kotlin object 单例，MMKV 存储分组排序、时间线预设 |
| `AppConfig` | 每应用配置（ShotConfig、ExcludeConfig、ActionConfig、ExtraItemsConfig） |
| `AppConfigManager` | 单例管理器，缓存 AppConfig 实例 |
| `GroupConfig` | 每分组 MMKV 实例配置 |
| `CompressItems` | 压缩项常量（DATA、USER、OBB、MEDIA 等） |

### `group` — 分组模型
| 类 | 职责 |
|---|------|
| `SnapGroup` | 分组数据类（id、name、path、apps），`name` 可选，回退到目录 basename |
| `ArchivedApp` | 分组内已存档应用 |

### `main` — 主界面
| 包 | 关键类 | 职责 |
|---|--------|------|
| `main` | `MainActivity` | 主 Activity：竖屏锁定、紧凑顶栏、悬浮底栏、权限检查、`floatingNavContentPaddingBottom()` |
| `main.launch` | `LauncherFragment`, `LauncherViewModel`, `GroupsAdapter`, `GroupItemAdapter`, `GroupActionsController`, `GroupBatchArchiver` | 存档 Tab：分组列表、快照/恢复操作 |
| `main.launch.addgroup` | `AddGroupBottomSheet` | 创建分组 BottomSheet |
| `main.launch.app` | `AppConfigFragment` | 应用配置 BottomSheet |
| `main.launch.config` | `ActionConfigManager`, `ExcludePatternsManager`, `ExtraItemsManager`, `ShotOptionsManager`, `VersionRetentionManager` | 配置管理器集合 |
| `main.launch.config.fragments` | `ExcludePatternBottomSheet`, `ExtraExcludePatternBottomSheet`, `ExtraItemEditBottomSheet`, `FilePickerBottomSheet` | 配置编辑 BottomSheet |
| `main.launch.group` | `GroupConfigFragment`, `GroupSettingFragment`, `PackageStatus` | 分组设置 |
| `main.launch.groupsort` | `GroupSortBottomSheet`, `GroupSortAdapter` | 拖拽排序分组 |
| `main.launch.makearchive` | `SnapshotCreator`, `SuccessSnapshotInfo` | 快照创建编排 |
| `main.launch.makearchive.progress` | `AbstractProgressDialog`, `GroupItemsProgressDialog`, `ItemProgressDialog` | 进度对话框 |
| `main.launch.popup` | `ArchiveItemAdapter`, `ArchiveItemPopupMenu` | 存档弹出菜单 |
| `main.launch.exception` | `ArchiveFailedException`, `RestoreFailedException` 等 | 异常类型 |

### `main.apps` — 应用列表 Tab
| 类 | 职责 |
|---|------|
| `AppsFragment`, `BaseAppsFragment` | 应用列表 Fragment |
| `AppsAdapter`, `AppsListComponent` | 列表适配和组件；loading = `isAppsLoading \|\| isLocalProcessing`，`appsList` 只做数据绑定（与 Timeline `isQuerying` 同模式） |
| `AppsViewModel` | 多维过滤 ViewModel（搜索、类型、标签、用户 ID） |

### `main.selectapp` — 应用选择
| 类 | 职责 |
|---|------|
| `SelectAppFragment`, `SelectAppAdapter` | 添加应用到分组的选择界面；loading 与应用 Tab 同契约（`AppsListComponent` → ProgressBar） |

### `main.timeline` — 时间线 Tab
| 类 | 职责 |
|---|------|
| `TimelineFragment` | 时间线主 Fragment，日期范围筛选、搜索、热力图 |
| `TimelineAdapter` | 列表适配器，搜索高亮 |
| `TimelineViewModel` | 时间线 ViewModel，多选、批量操作 |
| `TimelineRepository` | 数据查询，从 groupList 内存过滤 |
| `TimelineModels` | 数据模型（TimelineEntry、TimeRange、TimePreset） |
| `TimelineGrouping` | 时间段分组逻辑 |
| `TimelineBatchOperator` | 批量恢复/删除/导出 |
| `TimelineHeatmapView` | 热力图自定义 View |
| `TimelineStickyHeaderDecoration` | RecyclerView 粘性头部 |
| `TimelineListItem`, `TimelineTextHighlight` | 列表项模型、搜索高亮 |
| `RestoreStrategyDialog` | 恢复策略选择（新/旧快照优先） |

### `main.settings` — 设置
| 类 | 职责 |
|---|------|
| `SettingsActivity`, `SettingsAdapter` | 设置界面（独立 Activity，竖屏锁定，顶栏 + RecyclerView） |
| `IgnoreAppsFragment`, `IgnoreAppsConfig` | 忽略应用管理 |
| `AboutFragment` | 关于页面 |

### `repository` — 数据仓库
| 类 | 职责 |
|---|------|
| `AppDataRepository` | 单例仓库；`scope`（`SupervisorJob + IO`）执行 `loadGroups` / `addGroup` / `deleteGroup`；`loadGroupsMutex` 防并发 stale；`groupList` / `appsList` / `isAppsLoading` LiveData。`isAppsLoading` 为已安装应用 catalog 加载 SSOT：`loadData` 在 `loadGroups` 前即置 true（含分组加载窗口），`loadApps` finally 置 false；UI 禁止用 `appsList` 排放驱动 loading |

### `glide` — 图片加载
| 类 | 职责 |
|---|------|
| `SnapShotGlideModule` | Glide 模块注册 |
| `AppInfoDataFetcher`, `AppInfoModelLoader` | 应用图标加载器 |

### `ui.widget` — 自定义组件
| 类 | 职责 |
|---|------|
| `TagsFilterLayout` | 标签筛选：单行横向滚动 + 同行展开/收起；reparent 使用 `FrameLayout.LayoutParams`；样式 `Widget.AppSnapshot.Chip.Tag`（22dp，11sp） |
| `SearchFieldExt` | 搜索框扩展函数 |
| `CollapsibleSearchController` | 筛选行 `ImageView` 图标 ↔ `layout_search_field` 展开/收起（时间线、应用 Tab、选应用、忽略应用） |
| `FloatingBottomNav` | 悬浮底栏 `BlurView` 毛玻璃（采样根 `coordinator`） |

## 主界面布局（`activity_main.xml`）

```
ConstraintLayout (@id/coordinator)
├── LinearLayout toolbar_header — MaterialToolbar (48dp) + 分隔线
├── FragmentContainerView — NavHost，贴顶栏下方
└── BlurView — 悬浮胶囊底栏（176dp 固定宽）
    └── LinearLayout — 3× ImageButton Tab（26dp 图标）
```

顶栏固定不随列表滚动；列表通过 `floatingNavContentPaddingBottom()` 避让底栏。Tab 导航由 `MainActivity.setupBottomNavigation()` 绑定 `NavController`。

## 设置页布局（`activity_settings.xml`）

```
LinearLayout (@id/settings_root)
├── LinearLayout toolbar_header — MaterialToolbar (48dp) + 分隔线，标题「设置」，返回主界面
└── RecyclerView — 设置项；子页（忽略应用、关于）以 BottomSheet 打开
```

Insets 与主界面一致：`toolbar_header` 顶栏避让状态栏，`settings_root` 避让导航栏。详见 [`ui-shell.md`](../../guides/getting-started/ui-shell.md#设置页壳层)。

### `utils` — 工具类
| 类 | 职责 |
|---|------|
| `AppIconUtils` | 应用图标加载和缓存 |
| `AppShell` | Shell 命令执行 |
| `AppStatusHelper` | 应用状态检查 |
| `ApksUtil` | APK 相关工具 |
| `ArchiveRenameHelper` | 存档重命名 |
| `GroupPathPickerHelper` | SAF 路径选择器 |
| `JsonUtils` | JSON 序列化 |
| `PathHelper` | 路径操作 |

## 统计

| 指标 | 数量 |
|------|------|
| Kotlin 文件 | 87 |
| 包 | 24 |
| Activity | 2（MainActivity、SettingsActivity） |
| Fragment | 8+ |
| BottomSheetDialogFragment | 10 |
| ViewModel | 4（Snapshot、Launcher、Apps、Timeline） |
| Adapter | 11 |
| 自定义 View | 2（TagsFilterLayout、TimelineHeatmapView） |
| UI 控制器 | 2（CollapsibleSearchController、FloatingBottomNav） |

## 国际化

| 文件 | 说明 |
|------|------|
| `res/values/strings.xml` | 默认文案（简体中文，fallback） |
| `res/values-zh-rCN/strings.xml` | 显式简体中文 |
| `res/values-en/strings.xml` | 英文 |

约 **320** 条字符串，覆盖全部用户界面（存档/恢复、分组批量操作、应用与分组配置、时间线、应用 Tab 筛选、设置、进度对话框、菜单与 `contentDescription`）。新增文案须同步更新三份 locale 文件。详见 [`i18n 指南`](../../guides/getting-started/i18n.md)。

## 技术栈

- Kotlin + ViewBinding + DataBinding
- Material3 + Navigation Component
- Coroutines + Flow（`AppDataRepository.scope` 管全局分组/应用数据；各 Tab ViewModel 用 `viewModelScope`）
- Glide（kapt 注解处理器）
- FastJSON2（主）、Moshi、Gson（可用）

## 相关系统

| 系统 | 关系 |
|------|------|
| [快照系统](../../systems/snapshot/INDEX.md) | UI 入口、快照创建/恢复编排 |
| [分组集](../../systems/snapshot/GROUP_SET.md) | 存档 Tab 组织层：`SnapGroupSet` / `ArchiveListItem` / `archiveList` |
| [分组集折展性能](../../systems/snapshot/group-set-expand-perf.md) | Header 折展只内存再投影，禁止 `reloadGroupsLocked` |
| [时间线系统](../../systems/timeline/INDEX.md) | TimelineFragment 及全套时间线组件 |
| [配置系统](../../systems/config/INDEX.md) | GlobalConfig、AppConfig、ExcludePatternsManager |

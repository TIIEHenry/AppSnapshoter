---
title: "Group 批量恢复 — 现状分析"
type: system
status: draft
updated: 2026-06-17
summary: "现有数据模型、可复用能力与组头 UI 问题"
---

# 现状分析

[← 返回索引](../GROUP_BATCH_RESTORE.md)

---

## 2.1 数据模型

```
SnapGroup (分组)
  └── ArchivedApp (应用快照目录)
        └── ArchiveItem (单个快照)
              └── MetaInfo.makeTime  ← 快照创建时间（毫秒时间戳）
```

| 类 | 路径 | 说明 |
|----|------|------|
| `SnapGroup` | `group/SnapGroup.kt` | 分组，含 `apps: MutableList<ArchivedApp>`；配置经 `GroupConfig` |
| `ArchivedApp` | `group/ArchivedApp.kt` | 应用级快照容器；`latestArchive` 按 `makeTime` 取最大 |
| `ArchiveItem` | `archive/ArchiveItem.kt` | 单个快照实体 |
| `GroupConfig` | `config/GroupConfig.kt` | 分组 JSON 配置 + group 级 MMKV |

**当前缺失：** 无「上次恢复时间 / 上次恢复的快照」记录。本方案需新增 `RestoreRecord` 及 `RestoreRecordStore`。

## 2.2 数据加载链路

```
SnapshotApp.onCreate()
  → SnapshotViewModel.loadGroups()
    → AppDataRepository.loadGroups()
      → SnapGroup.loadApps() → ArchivedApp.loadArchives()
```

全局数据源：`SnapshotViewModel.groupList: LiveData<List<SnapGroup>>`

## 2.3 现有恢复能力

| 能力 | 实现 | 说明 |
|------|------|------|
| 单应用恢复最新 | `ArchiveRestorer.restoreLatest()` | 点击应用行 → `LauncherViewModel.onGroupItemClicked()` |
| 单应用恢复指定快照 | `ArchiveRestorer.restoreArchiveItem()` | 快照列表 / 弹窗菜单选具体 `ArchiveItem` |
| 高级恢复 | `ArchiveRestorer.restoreAdvanced()` | 选择 data/apk/obb 子集 |
| 无 UI 挂起恢复 | `ArchiveRestorer.restoreArchiveSuspend()` | 批量场景使用 |
| 时间线批量恢复 | `TimelineBatchOperator.batchRestore()` | 跨组 + 时间区域 + 多选 |

单条恢复核心流程（`ArchiveRestorer.restoreArchive`）：

1. 已安装 → `clearAppData`
2. 按需安装 APK
3. `DataRestorer` 解压数据项
4. `PermissionRestorer` 恢复权限 / AppOps / SSAID

## 2.4 现有批量归档能力

| 组件 | 路径 | 说明 |
|------|------|------|
| `GroupBatchArchiver` | `main/launch/GroupBatchArchiver.kt` | 串行创建快照、进度、成功/失败汇总 |
| `GroupActionsController` | `main/launch/GroupActionsController.kt` | 组头按钮事件；`btn_archive_all` → `GroupBatchArchiver.archiveAllApps` |
| `GroupItemsProgressDialog` | `main/launch/makearchive/progress/` | 多项进度对话框 |

归档过滤逻辑（**恢复不应完全对称**）：

- 仅 **已安装** 应用
- 受 `ActionConfig.isAutoSnapshot` 控制（应用级配置优先于组级）

## 2.5 组头 UI 现状与问题

布局文件：`app/src/main/res/layout/item_group.xml`

当前结构：**单行** — 标题（18sp）与最多 6 个 `40×40dp` 按钮并列（`btn_confirm` 排序模式下可见，平时 gone）：

| 按钮 | id | 图标 | 功能 |
|------|-----|------|------|
| 确认排序 | `btn_confirm` | check | 自定义排序模式（通常 gone） |
| 刷新 | `btn_refresh` | refresh | 重载组内应用；长按显示统计 |
| 添加 | `btn_add` | app_add | 添加应用到组 |
| 排序 | `btn_move` | group_sort | 排序方式 PopupMenu |
| 设置 | `btn_tune` | tune | 分组配置 |
| 全部归档 | `btn_archive_all` | briefcase_download_outline | 批量创建快照 |

**空间估算（360dp 屏宽，常规模式 5 个可见按钮）：**

- 5 × (40 + 8 margin) = **240dp**
- 扣除 padding 后标题区约 **100dp**，长组名易截断
- 若再加独立「批量恢复」按钮，标题区基本不可用 → 本方案改为 **双行 + 批量菜单**

## 2.6 可复用能力清单

| 能力 | 现有实现 | 本功能复用方式 |
|------|----------|----------------|
| 批量进度 UI | `GroupItemsProgressDialog` | 直接使用 |
| 成功/失败列表 | `GroupBatchArchiver` 内 dialog + adapter | 提取或镜像 |
| 快照选取（新/旧） | `TimelineRepository.resolveArchive()` + `RestoreStrategy` | 抽取 `ArchiveResolver`；Group 扩展 `LAST_RESTORED` |
| 挂起恢复 | `ArchiveRestorer.restoreArchiveSuspend()` | 批量循环调用（与 Timeline 相同） |
| 安装状态判断 | `AppStatusHelper.isAppInstalled()` | 范围过滤 |
| 策略对话框布局 | `dialog_restore_strategy.xml` | 参考样式，新建 **范围 + 策略** 组合对话框 |
| 防重复提交 | `TimelineViewModel.isBatchRunning` | **上移至** `SnapshotViewModel.isBatchRunning`，存档 / 时间线 Tab 共用 |
| 批量执行模板 | `TimelineBatchOperator.batchRestore` | `GroupBatchRestorer` 逐项对照（见 [附录 §A.1](../07-appendix.md#a1-与-timelinebatchoperator-的对照)） |

## 2.7 与时间线批量恢复的差异

| | 时间线 | Group 批量恢复 |
|--|--------|----------------|
| 范围 | 跨组、用户多选、`TimeRange` | 单组、枚举范围 |
| 快照候选集 | 时间区域内匹配的 archives | 该应用组内全部 archives |
| 入口交互 | 多选工具栏 → 可选策略对话框 → 确认 | 批量菜单 → **统一配置对话框**（范围 + 策略 + 预览） |
| 运行时解析 | `TimelineRepository.resolveEntry(key, groups, range)` | 对话框阶段从 `group.apps` 构建 plan；执行循环内 `resolveTaskAt(task, groupList)` 重查 |

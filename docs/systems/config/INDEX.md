---
title: "配置系统"
type: system
status: active
updated: 2026-06-17
summary: "MMKV 持久化、分组配置、排除规则、保留策略和全局设置"
---

# 配置系统

## 概述

AppSnapshoter 使用 MMKV 作为唯一的配置持久化机制，辅以文件系统上的 JSON 配置文件。配置分为三层：全局配置、分组配置、应用配置。

## 配置层级

### 全局配置

**文件**：`app/.../config/GlobalConfig.kt`（Kotlin object 单例）

存储在 MMKV 默认实例中：

| 字段 | 类型 | 说明 |
|------|------|------|
| `groupOrder` | `List<String>` | 分组 ID 排序 |
| `timelinePreset` | `String` | 时间线筛选预设（today/yesterday/7d/30d/custom） |
| `timelineCustomStart` | `Long` | 自定义起始时间戳 |
| `timelineCustomEnd` | `Long` | 自定义结束时间戳 |

### 分组配置

**文件**：`app/.../config/GroupConfig.kt`

每个分组独立 MMKV 实例 + `group.json` 文件：
- `group.json` 中 `name` 字段可选，缺省时 `SnapGroup.name` 回退到目录 basename，再回退到 group `id`
- 分组路径（`rootPath`）可自定义，默认在 `/storage/emulated/0/Android/snapshot/` 下

### 应用配置

**文件**：`app/.../config/AppConfig.kt`、`AppConfigManager.kt`

每应用配置由 4 个子配置组成：

| 子配置 | 说明 |
|--------|------|
| `ShotConfig` | 快照选项（启用状态、权限、包含项） |
| `ExcludeConfig` | 文件排除规则（按压缩类型分类） |
| `ActionConfig` | 快照后动作 |
| `ExtraItemsConfig` | 额外压缩项列表 |

`AppConfigManager` 单例管理器缓存 `AppConfig` 实例，避免重复读取。

## 配置 UI 管理器

| 管理器 | 文件 | 职责 |
|--------|------|------|
| `ShotOptionsManager` | `app/.../config/ShotOptionsManager.kt` | 快照选项 UI |
| `ExcludePatternsManager` | `app/.../config/ExcludePatternsManager.kt` | 排除规则 UI |
| `ExtraItemsManager` | `app/.../config/ExtraItemsManager.kt` | 额外压缩项 UI |
| `ActionConfigManager` | `app/.../config/ActionConfigManager.kt` | 快照后动作 UI |
| `VersionRetentionManager` | `app/.../config/VersionRetentionManager.kt` | 版本保留策略 UI |

## 排除规则

**文件**：`app/.../config/ExcludePatternsManager.kt`

管理不纳入快照的文件/目录匹配模式：
- 按压缩类型（DATA、OBB、MEDIA 等）分类
- 支持内置规则和自定义规则
- BottomSheet UI：`ExcludePatternBottomSheet`、`ExtraExcludePatternBottomSheet`

## 压缩项常量

**文件**：`app/.../config/CompressItems.kt`

| 常量 | 说明 |
|------|------|
| `DATA` | 应用内部数据 `/data/data/{pkg}` |
| `USER` | 外部存储数据 |
| `OBB` | OBB 扩展文件 |
| `MEDIA` | 媒体文件 |
| `APK` | 应用 APK |

## 涉及模块

| 模块 | 职责 |
|------|------|
| [`app`](../../modules/app/INDEX.md) | 全部配置类和配置 UI |
| [`provider`](../../modules/provider/INDEX.md) | 快照时读取应用配置 |

## 相关文档

- [存储策略](../../architecture/cross-cutting/storage.md)

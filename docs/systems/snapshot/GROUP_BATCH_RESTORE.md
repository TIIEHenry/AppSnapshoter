---
title: "Group 批量恢复功能设计"
type: system
status: draft
updated: 2026-06-17
summary: "存档 Tab 分组级批量恢复：可配置恢复范围与快照策略，并重构组头工具栏布局"
---

# Group 批量恢复功能设计文档

> 版本：v1.0 · 日期：2026-06-17 · 状态：待评审

---

## 文档索引

| 章节 | 文档 | 内容 |
|------|------|------|
| 1 | [背景与目标](group-batch-restore/01-overview.md) | 需求背景、目标、非目标、已定决策 |
| 2 | [现状分析](group-batch-restore/02-current-state.md) | 数据模型、现有能力、与时间线的差异 |
| 3 | [UI 设计](group-batch-restore/03-ui-design.md) | 组头布局重构、批量菜单、配置对话框 |
| 4 | [核心业务逻辑](group-batch-restore/04-business-logic.md) | 恢复范围、快照策略、RestoreRecord、执行流程 |
| 5 | [模块与文件结构](group-batch-restore/05-implementation.md) | 新增/修改文件、类职责 |
| 6 | [边界 / 测试 / 实施计划](group-batch-restore/06-quality-roadmap.md) | 边界情况、测试范围、Phase 划分与验收 |

---

## 快速摘要

在 **存档 Tab** 的分组卡片上，为「全部归档」补齐对称的 **批量恢复** 能力。用户可通过配置对话框选择：

- **恢复范围**：未安装的应用 / 全部有快照的应用 / 自上次恢复以来有更新的应用
- **快照选择**：最新 / 最旧 / 与上次恢复相同

组头工具栏改为 **标题一行 + 图标一行**，并将「全部归档 / 批量恢复」合并为 **批量操作菜单**，避免按钮过多导致布局溢出。

执行层复用 `ArchiveRestorer.restoreArchiveSuspend` 与 `GroupItemsProgressDialog`，编排模式对齐 `TimelineBatchOperator` 与 `GroupBatchArchiver`。

---

## 阅读建议

| 角色 | 建议阅读顺序 |
|------|--------------|
| 产品 / 评审 | [01 背景与目标](group-batch-restore/01-overview.md) → [03 UI 设计](group-batch-restore/03-ui-design.md) → [06 验收标准](group-batch-restore/06-quality-roadmap.md#验收标准) |
| 开发 | [02 现状](group-batch-restore/02-current-state.md) → [04 业务逻辑](group-batch-restore/04-business-logic.md) → [05 实现结构](group-batch-restore/05-implementation.md) |
| 测试 | [06 边界与测试](group-batch-restore/06-quality-roadmap.md) |

---

## 与时间线批量恢复的关系

| 维度 | 时间线 Tab | Group 批量恢复（本方案） |
|------|------------|--------------------------|
| 入口 | 时间线多选工具栏 | 分组卡片 → 批量菜单 → 配置对话框 |
| 范围定义 | 时间区域 + 用户多选 | 组内 + 范围枚举（未安装 / 全部 / 自上次恢复以来） |
| 快照策略 | 新快照优先 / 旧快照优先 | 最新 / 最旧 / 与上次相同 |
| 恢复引擎 | `ArchiveRestorer.restoreArchiveSuspend` | 相同 |
| 进度 UI | `GroupItemsProgressDialog` | 相同 |

两者互补：时间线解决 **跨组、按时间段** 的批量操作；Group 批量恢复解决 **单组、按安装状态 / 增量** 的批量操作。

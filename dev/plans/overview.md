---
title: "实施计划总览"
type: plan
status: active
updated: 2026-08-21
summary: "所有实施阶段概览、里程碑和当前焦点"
---

# 实施计划总览

## 阶段列表

| 阶段 | 状态 | 计划文件 | 说明 |
|------|------|---------|------|
| 核心功能 | completed | — | 快照/恢复、压缩管线、分组管理 |
| 时间线 v1.0 | completed | — | 时间线 Tab MVP |
| 时间线 v1.1 | completed | — | 批量操作、热力图 |
| 时间线 v1.2 | completed | — | 恢复策略、UI 优化 |
| 文档系统 | completed | [roadmap](../roadmap/active/phase-doc-system.md) | docs/ + dev/ 两层文档体系 |
| 架构加固 | planning | [2026-08-arch-hardening](2026-08-arch-hardening.md) | 任务契约重写、FIFO 看门狗、root 边界收口（源于[架构审查 2026-08](../../docs/architecture/review-2026-08.md)） |
| 分组集 | implemented | [2026-08-group-set](2026-08-group-set.md) | 父目录组织多个分组；存档 Tab 连续成块（[设计](../../docs/systems/snapshot/GROUP_SET.md)） |

## 当前焦点

架构加固计划（planning）：先行 Phase A–C 正确性批次——`ITaskHandler` 契约重写 + 错误码通道、FIFO 看门狗、SELinux/chown 下沉与 `callTarCli` 白名单。

分组集已落地（implemented）；可选：时间线「集名 / 分组名」、底栏拖选 hover。

## 历史计划

完成的计划可移入 `dev/roadmap/archive/` 归档。

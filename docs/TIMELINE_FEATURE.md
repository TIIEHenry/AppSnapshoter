# 首页「时间线」Tab 功能设计文档

> 版本：v1.2 · 日期：2026-06-16 · 状态：已实施

---

## 文档索引

| 章节 | 文档 | 内容 |
|------|------|------|
| 1 | [背景与目标](timeline/01-overview.md) | 需求背景、目标、非目标、已定决策 |
| 2 | [现状分析](timeline/02-current-state.md) | 数据模型、加载链路、可复用能力、导航结构 |
| 3 | [功能设计](timeline/03-ui-design.md) | 信息架构、列表粒度、时间筛选、多选交互 |
| 4 | [核心业务逻辑](timeline/04-business-logic.md) | 数据模型、查询、恢复/删除流程、数据流 |
| 5 | [模块与文件结构](timeline/05-implementation.md) | 新增/修改文件、ViewModel 状态 |
| 6 | [边界 / 性能 / 测试](timeline/06-quality.md) | 边界情况、性能方案、单元测试范围 |
| 7 | [实施计划与验收](timeline/07-roadmap.md) | Phase 1~3 任务清单、验收标准 |
| 8 | [附录](timeline/08-appendix.md) | 集成点代码片段、v1.0→v1.1 变更摘要 |

---

## 快速摘要

在首页底部导航新增 **时间线** Tab（顺序：`存档 | 时间线 | 应用`），按时间区域跨分组展示有快照的应用，支持多选后批量恢复（新/旧快照策略）或批量删除（区域内全部匹配快照）。

- **数据源**：内存过滤 `SnapshotViewModel.groupList`，不额外扫盘
- **列表粒度**：`(groupId, packageName, userId)` 一行
- **首版工期**：Phase 1 MVP 约 2~3 天（见 [实施计划](timeline/07-roadmap.md)）

---

## 阅读建议

| 角色 | 建议阅读顺序 |
|------|--------------|
| 产品 / 评审 | [01 背景与目标](timeline/01-overview.md) → [03 功能设计](timeline/03-ui-design.md) → [07 验收标准](timeline/07-roadmap.md#验收标准) |
| 开发 | [02 现状](timeline/02-current-state.md) → [04 业务逻辑](timeline/04-business-logic.md) → [05 实现结构](timeline/05-implementation.md) → [08 附录](timeline/08-appendix.md) |
| 测试 | [06 边界与测试](timeline/06-quality.md) → [07 验收标准](timeline/07-roadmap.md#验收标准) |

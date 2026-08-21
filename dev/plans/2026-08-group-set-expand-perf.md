---
title: "分组集折展性能实施计划"
type: plan
status: implemented
updated: 2026-08-21
summary: "Phase A 已落地：锁内工作集 + 再投影只发 archiveList；折展/一键折叠禁止 reloadGroupsLocked。Phase B 未做"
---

# 分组集折展性能实施计划

> 阶段：Phase A（已落地）；Phase B **out of scope**  
> 状态：implemented  
> 审查：Grok 第三轮 **Approve**  
> 关联：[方案](../../docs/systems/snapshot/group-set-expand-perf.md) · [分组集设计](../../docs/systems/snapshot/GROUP_SET.md)

## 目标

点分组集 Header 折展、以及一键折叠，不再对全部分组 `loadApps(reload=true)`。只写 `isCollapsed` 后，用锁内工作集内存投影 `archiveList`。保持投影 SSOT，禁止 Adapter 本地插删或 Header 藏卡片。

## 任务清单

### Phase A: 内存再投影（必须）

- [x] `AppDataRepository` 增加 mutex 保护的 `loadedGroups` / `loadedSets`；**mutex 内唯一读源**（禁止任何 `withLock` 路径读 `groupList.value` / `groupSetList.value`）
- [x] `reloadGroupsLocked`：`existingGroups` / `existingSets` 从工作集取；建成后覆盖工作集，再 `postValue(groupList/groupSetList)`，再 `reprojectArchiveListLocked()`
- [x] `reprojectArchiveListLocked()`：只读工作集 + `archiveRoots`，`deriveLiveMembers` + `project` + `materialize`，**只** `archiveList.postValue`
- [x] 其余 mutex 读点改 `loaded*`
- [x] `setGroupSetCollapsed(setId, collapsed)`：去掉 FS；工作集找不到则 no-op；只再投影
- [x] `collapseAllArchive()`：遍历 `loaded*` 写 `isCollapsed` 后只再投影
- [x] `SnapshotViewModel` 折展/一键折叠不再 `appDeps()`
- [x] `ArchiveListItem.GroupCard` 增加投影快照 `collapsed`；`materializeArchiveList` 写入
- [x] `ArchiveDiffCallback` 只比较 `collapsed` 快照；KDoc 禁止读 live getter
- [x] `GROUP_SET.md` / `add-app-refresh-stale-group.md` 交叉文档已同步

### Phase B: 展开绑定（out of scope）

**未做。** 仅当 Trace 显示扫盘已消失、展开仍掉帧时另开计划。

## 验收标准

- [x] 代码路径：折展无 `reloadGroupsLocked` / `loadApps`；mutex 内无 `*.value`
- [x] 真机：点 Header 折展无 `loadGroup:`；一键折叠只留 Header + 独立组
- [ ] 真机：`requestNavigateToGroup` 不丢事件（未测）

## 风险与依赖

见方案文档。落地时已用工作集替换全部 mutex 读点。

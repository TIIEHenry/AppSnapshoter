---
title: "分组集实施计划"
type: plan
status: implemented
updated: 2026-08-21
summary: "Phase 1–3 已落地：模型/投影/UI/两级排序/底栏 PopupMenu 快跳；可选时间线集名显示未做"
---

# 分组集实施计划

> 阶段：Phase 1–3  
> 状态：implemented  
> 关联：[分组集功能设计 v1.2](../../docs/systems/snapshot/GROUP_SET.md)

## 目标

用父目录组织多个分组：添加分组集时扫描直接子目录并登记；存档 Tab 由 repository 投影 `archiveList`，同一集连续成块、默认折叠。快照单元仍是 `SnapGroup`。`groups` 只做本机 ID 登记，不是 UI 顺序。

## 任务清单

### Phase 1: 模型与扫描

- [x] `SnapGroupSet`、`GroupSetConfig`、`groupset.json`（`groupOrder` = 子目录 basename）
- [x] `ConfigFiles.GROUP_SET_CONFIG_FILE`；**不**加 `group_sets_order`
- [x] `GlobalConfig.archiveRoots`；**仅键不存在**时由现 `groups` 迁成全 `g:` 并写出键；`groups` 保持登记表
- [x] `ArchiveListProjector.project` 纯函数
- [x] `reloadGroupsLocked` 末尾投影 `archiveList`；集折展 / 排序保存走同一 mutex
- [x] `discoverGroups` / `addGroupSet` / `deleteGroupSet` / `addGroup` / `deleteGroup` / 改 path：全部进 `loadGroupsMutex`
- [x] 单测：派生、连续块、空集 hint、排序不改 `groups` ID 集合

### Phase 2: 存档 UI

- [x] `GroupsAdapter` 只吃 `ArchiveListItem`；`LauncherFragment` 只观察 `archiveList`
- [x] SetHeader：折叠、刷新、设置；空集「在此添加分组」预填路径
- [x] 工具栏：添加分组 / 添加分组集；空 SnapGroup 升级为集
- [x] `GroupSetSettingFragment` + 删除三档；改 path 走 repository
- [x] `GroupSettingFragment` 改 path 走 repository
- [x] i18n：`group_set_*` 三份 locale

### Phase 3: 排序与跳转

- [x] `GroupSortBottomSheet` 两级；只写 `archiveRoots` / basename `groupOrder`
- [x] `navigateToGroup`：`submitList { tryConsumeNavigate() }`；时间线走 `requestNavigateToGroup`
- [x] `navigateToGroupSet` 滚 Header 不改折叠
- [x] `GroupSetJumpTouchSession`：超时后 PopupMenu（未移植 Singular 拖选 hover）
- [ ] （可选）时间线条目 `集名 / 分组名`

## 验收标准

- [x] 代码路径覆盖添加/投影/连续块/排序不改 `groups` / 跳转 commit 消费
- [ ] 真机：删除三档、时间线展开跳转、快照/恢复回归

## 风险与依赖

见设计文档；排序与 `navigateToGroup` 旧协议已在实现中规避。

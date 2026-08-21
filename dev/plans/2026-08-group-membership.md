---
title: "分组应用归属与移动实施计划"
type: plan
status: implemented
updated: 2026-08-21
summary: "Phase 1 已落地：repository 守卫、Guard、move、Setting 开关、冲突对话框；Phase 2 体验项未做"
---

# 分组应用归属与移动实施计划

> 阶段：Phase 1 MVP  
> 状态：implemented（Phase 1）  
> 关联：[分组应用归属与移动 v1.2](../../docs/systems/snapshot/GROUP_MEMBERSHIP.md)

## 目标

独占归属不变量由 `AppDataRepository` 强制；进程级占用 Guard；`AddAppsResult`/`MoveAppResult` 替换 Unit 回调；冲突可移动；D7 多独占检测。

## 任务清单

### Phase 1: MVP

- [x] `membershipMode` + Resolver + 结果类型
- [x] PackageOpGuard（全局批 + packageDir）；VM facade；SnapshotCreator 登记（批内跳过 package 登记）
- [x] `setMembershipMode`（repository）；SettingFragment 开关
- [x] `addAppsToGroup` 守卫 + `AddAppsResult`
- [x] D7 加载检测 Log.w；修复 = shared 或删成员
- [x] Controller：Conflict → 对话框 → move（逐项）
- [x] move：FS 契约、残缺目标、图标、元数据、`RestoreRecordStore.remove`
- [x] ArchiveMaker 独占防御
- [x] `groupedAppKeys` 仅独占；i18n
- [ ] 文档：刷新回调契约、批 SSOT 叙述（可后续补）

### Phase 2: 可选

- [ ] 列表角标 / 部分移动 / 共享弱提示 / 修复向导 UI

## 验收标准

见设计 §验收标准；真机回归待测。

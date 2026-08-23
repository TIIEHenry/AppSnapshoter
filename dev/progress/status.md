---
title: "当前开发状态"
type: progress
status: active
updated: 2026-08-23
summary: "应用 catalog 用 isAppsCatalogLoaded 区分未拉取与空表；架构加固计划仍在下一步"
---

# 当前开发状态

> 最后更新：2026-08-23

## 当前阶段

稳定性与 UI 打磨 — v1.1.x；分组集（组织层）已落地。

## 已完成功能

- [x] 一键存档/恢复
- [x] TAR + ZSTD 流式压缩管线
- [x] APK 智能去重
- [x] 多存档管理
- [x] 自定义压缩项
- [x] 分组管理（创建、排序、配置）
- [x] 时间线视图（v1.2）
- [x] 批量恢复/删除/导出
- [x] Syncthing 同步支持
- [x] 文档系统建设（docs/ + dev/ 两层体系）
- [x] 主界面 UI 焕新（Fluent 2 主题、紧凑顶栏、悬浮毛玻璃底栏、可折叠搜索）
- [x] 应用 Tab 筛选行图标化（系统/用户切换 + 标签展开/收起）
- [x] 崩溃修复批次（标签 LayoutParams、AppTag NPE、JNI 泄漏、Root 服务空指针、Timeline 观察者泄漏等）
- [x] 应用 catalog loading SSOT（`isAppsLoading` + `isAppsCatalogLoaded`；未拉取保持 loading；可见时有限次退避重试）
- [x] 分组 body 三态可见性（空组优先加号；有应用才折/展）
- [x] 分组集（`archiveList` 投影、集折叠块、两级排序、底栏长按快跳含拖选）
- [x] 分组集强调色（预设 + 自定义；投影快照刷新）
- [x] 选择应用 BottomSheet：打开重置过滤、「未分组」开关
- [x] 统一添加分组/集 BottomSheet；集内色条与一键折叠
- [x] 存档列表打磨：SetHeader 吸顶 overlay、整行按压无 ripple、组内网格超过 3 行自滚

- [x] 分组集折展性能 Phase A（`loadedGroups`/`loadedSets` 工作集；折展/一键折叠只再投影，不扫盘）

## 进行中

_无活跃开发任务_

## 已知问题

_无_

## 下一步

- [架构加固计划](../plans/2026-08-arch-hardening.md)（planning）：落实[架构审查报告 2026-08](../../docs/architecture/review-2026-08.md) 的 P1 项——任务契约重写、FIFO 看门狗、root 边界收口
- （可选）时间线条目显示「集名 / 分组名」；折展 Phase B（展开绑定）若真机仍掉帧再开
- 真机验：时间线跳转折叠集内分组（`requestNavigateToGroup`）

---
title: "当前开发状态"
type: progress
status: active
updated: 2026-08-21
summary: "分组集 UI 打磨已提交；架构加固计划仍为下一步"
---

# 当前开发状态

> 最后更新：2026-08-21

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
- [x] 应用 catalog loading SSOT（`isAppsLoading`；应用 Tab / 选择应用 BottomSheet）
- [x] 分组 body 三态可见性（expand/empty/content 互斥；空组折叠不再双图标）
- [x] 分组集（`archiveList` 投影、集折叠块、两级排序、底栏长按快跳含拖选）
- [x] 分组集强调色（预设 + 自定义；投影快照刷新）
- [x] 选择应用 BottomSheet：打开重置过滤、「未分组」开关
- [x] 统一添加分组/集 BottomSheet；集内色条与一键折叠

## 进行中

_无活跃开发任务_

## 已知问题

_无_

## 下一步

- [架构加固计划](../plans/2026-08-arch-hardening.md)（planning）：落实[架构审查报告 2026-08](../../docs/architecture/review-2026-08.md) 的 P1 项——任务契约重写、FIFO 看门狗、root 边界收口
- （可选）时间线条目显示「集名 / 分组名」；底栏快跳升级为拖选 hover

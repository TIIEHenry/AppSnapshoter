---
title: "AppSnapshoter 文档索引"
type: index
status: active
updated: 2026-06-17
summary: "全局文档导航索引，按角色和主题组织所有文档入口"
---

# AppSnapshoter 文档索引

> 本文件是文档系统的主入口。按**主题**和**角色**两条线组织，帮助快速定位所需文档。

---

## 快速入口

| 目标 | 入口 |
|------|------|
| 了解项目 | [README](../README.md) → [设计哲学](../DESIGN.md) → [架构总览](architecture/overview.md) |
| 开始开发 | [AGENTS.md](../AGENTS.md) → [模块索引](#模块文档) → [快照系统](systems/snapshot/INDEX.md) |
| 功能设计 | [系统文档](#系统文档) → [时间线系统](systems/timeline/INDEX.md) |
| 构建与部署 | [构建指南](guides/getting-started/build.md) → [故障排除](guides/getting-started/troubleshooting.md) |
| 术语查询 | [术语表](glossary.md) |

---

## 架构文档

| 文档 | 说明 |
|------|------|
| [架构总览](architecture/overview.md) | 模块关系、依赖图、技术栈全景 |
| [压缩管线](architecture/compression-pipeline.md) | TAR+ZSTD 流式压缩架构 |
| [Root 服务架构](architecture/root-service.md) | AIDL + libsu IPC 设计 |
| [安全策略](architecture/cross-cutting/security.md) | Root 权限、数据安全、IPC 安全 |
| [存储策略](architecture/cross-cutting/storage.md) | MMKV、文件布局、Syncthing 同步 |

---

## 系统文档

| 系统 | 说明 | 关键模块 |
|------|------|----------|
| [快照系统](systems/snapshot/INDEX.md) | 备份/恢复核心流程 | provider, io-* |
| [Group 批量恢复](systems/snapshot/GROUP_BATCH_RESTORE.md) | 分组批量恢复方案（待实施） | app/launch/ |
| [多用户适配](systems/snapshot/multi-user-adaptation.md) | 多用户场景适配现状与已知问题 | app, provider |
| [压缩系统](systems/compression/INDEX.md) | TAR 打包 + ZSTD 压缩管线 | io-tar, io-zstd, io-nativefs |
| [时间线系统](systems/timeline/INDEX.md) | 按时间浏览快照、批量操作 | app/timeline/ |
| [配置系统](systems/config/INDEX.md) | MMKV 持久化、分组配置、排除规则 | app/config/ |

---

## 模块文档

| 模块 | 说明 | 文档 |
|------|------|------|
| `app` | UI 层 — Activities, Fragments, ViewModels | [INDEX](modules/app/INDEX.md) |
| `api` | AIDL 接口 + Java 接口契约 | [INDEX](modules/api/INDEX.md) |
| `provider` | Root 服务实现 | [INDEX](modules/provider/INDEX.md) |
| `hiddenapi` | 反射访问隐藏 API | [INDEX](modules/hiddenapi/INDEX.md) |
| `systemapi` | 系统类桩 | [INDEX](modules/systemapi/INDEX.md) |
| `io-nativefs` | JNI 原生文件系统操作 | [INDEX](modules/io-nativefs/INDEX.md) |
| `io-tar` | JNI TAR 归档读写 | [INDEX](modules/io-tar/INDEX.md) |
| `io-zstd` | JNI ZSTD 压缩 | [INDEX](modules/io-zstd/INDEX.md) |

---

## 用户指南

| 文档 | 说明 |
|------|------|
| [快速开始](guides/getting-started/quickstart.md) | 安装、首次使用流程 |
| [构建指南](guides/getting-started/build.md) | 编译、调试、安装 |
| [Syncthing 同步](guides/getting-started/syncthing.md) | 跨设备同步配置 |
| [故障排除](guides/getting-started/troubleshooting.md) | 常见问题与解决 |

---

## 模板

| 模板 | 用途 |
|------|------|
| [架构决策记录](templates/decision-template.md) | ADR 格式 |
| [实施计划](templates/plan-template.md) | Phase 计划格式 |
| [功能设计](templates/feature-template.md) | 功能设计文档格式 |

---

## 阅读路径

| 角色 | 建议路径 |
|------|----------|
| **新开发者** | AGENTS.md → [设计哲学](../DESIGN.md) → [架构总览](architecture/overview.md) → [目标模块 INDEX](#模块文档) |
| **贡献者** | [快照系统](systems/snapshot/INDEX.md) 或 [时间线系统](systems/timeline/INDEX.md) → 相关模块文档 |
| **用户** | [README](../README.md) → [快速开始](guides/getting-started/quickstart.md) |
| **AI 助手** | CLAUDE.md → AGENTS.md → [设计哲学](../DESIGN.md) → [架构总览](architecture/overview.md) → [术语表](glossary.md) |

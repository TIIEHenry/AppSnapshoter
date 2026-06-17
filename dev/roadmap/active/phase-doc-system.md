---
title: "文档系统建设路线图"
type: plan
status: active
updated: 2026-06-17
summary: "当前阶段 — 建立 docs/ + dev/ 两层文档体系"
---

# Phase: 文档系统建设

> 状态：in_progress
> 预计完成：2026-06-17

## 任务清单

### 核心框架
- [x] 创建 docs/ 目录结构（architecture、systems、modules、guides、templates）
- [x] 创建 dev/ 目录结构（progress、plans、decisions、roadmap）
- [x] 编写 docs/INDEX.md 全局索引
- [x] 编写 docs/README.md 文档系统概述
- [x] 编写 docs/DOCS-SPEC.md 文档规范
- [x] 编写 docs/glossary.md 术语表
- [x] 编写 DESIGN.md 设计哲学

### 架构文档
- [x] architecture/overview.md — 系统架构总览
- [x] architecture/compression-pipeline.md — 压缩管线
- [x] architecture/root-service.md — Root 服务架构
- [x] architecture/cross-cutting/security.md — 安全策略
- [x] architecture/cross-cutting/storage.md — 存储策略

### 系统文档
- [x] systems/snapshot/INDEX.md — 快照系统
- [x] systems/compression/INDEX.md — 压缩系统
- [x] systems/timeline/INDEX.md — 时间线系统
- [x] systems/config/INDEX.md — 配置系统

### 模块文档
- [x] 8 个模块 INDEX（app、api、provider、hiddenapi、systemapi、io-nativefs、io-tar、io-zstd）

### 用户指南
- [x] guides/getting-started/quickstart.md
- [x] guides/getting-started/build.md
- [x] guides/getting-started/syncthing.md
- [x] guides/getting-started/troubleshooting.md

### 模板
- [x] templates/decision-template.md
- [x] templates/plan-template.md
- [x] templates/feature-template.md

### 开发追踪
- [x] dev/progress/status.md
- [x] dev/plans/overview.md
- [x] dev/decisions/001-two-layer-doc-system.md

### 质量保障
- [x] 全部链接验证通过
- [x] AGENTS.md 更新引用
- [x] STRUCTURE.md 标记归档并重定向

## 验收标准

- [x] docs/INDEX.md 所有链接指向存在的文件
- [x] 每个模块有 INDEX.md
- [x] 每个系统有 INDEX.md
- [x] 所有新 .md 文件有 frontmatter
- [x] AGENTS.md 引用新文档系统

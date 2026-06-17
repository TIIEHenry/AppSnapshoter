---
title: "文档规范"
type: guide
status: active
updated: 2026-06-17
summary: "文档系统的编写规范、格式要求和维护流程"
---

# 文档规范 v1.0

## 适用范围

本规范适用于 `docs/` 和 `dev/` 下的所有 Markdown 文件。

## Frontmatter 要求

### 必填字段

```yaml
---
title: "人类可读标题"
type: architecture | system | module | guide | decision | plan | progress | index | template
status: draft | active | completed | archived
updated: YYYY-MM-DD
summary: "一句话描述"
---
```

### 可选字段

```yaml
created: YYYY-MM-DD    # 长期文档的创建日期
phase: N               # 关联的实施阶段
```

### 状态生命周期

```
draft → active → completed → archived
                ↗
           (可跳过 completed 直接到 archived)
```

## 文档类型定义

| type | 用途 | 位置 |
|------|------|------|
| `architecture` | 系统级架构设计 | `docs/architecture/` |
| `system` | 跨模块功能系统文档 | `docs/systems/*/` |
| `module` | 单模块导航索引 | `docs/modules/*/` |
| `guide` | 用户/开发者指南 | `docs/guides/` |
| `decision` | 架构决策记录 | `dev/decisions/` |
| `plan` | 实施计划 | `dev/plans/` |
| `progress` | 进度追踪 | `dev/progress/` |
| `index` | 索引文件 | 各目录 `INDEX.md` |
| `template` | 文档模板 | `docs/templates/` |

## 编写规范

### 标题层级

- `#` — 文档标题（每个文件仅一个）
- `##` — 主要章节
- `###` — 子章节
- 不超过四级标题

### 链接

- 使用相对路径：`[架构总览](../architecture/overview.md)`
- 同目录内可省略路径：`[详情](detail.md)`
- 外部链接使用完整 URL

### 代码块

- 使用围栏代码块（` ``` `），标注语言
- 配置文件、命令行、代码片段均需代码块

### 表格

- 用于结构化对比数据
- 保持列对齐

### 图表

- 优先使用 Mermaid 图（支持 GitHub 渲染）
- 截图放在 `docs/screenshots/`

## 提交规则

### 何时更新文档

| 变更类型 | docs/ | dev/ |
|----------|-------|------|
| 新功能 | 更新相关系统文档 | 创建/更新计划、ADR |
| 行为变更 | 更新受影响文档 | — |
| 架构决策 | — | 创建 ADR |
| Bug 修复（简单） | 可免 | 可免 |
| Bug 修复（重大） | 更新 | 创建 ADR |
| 重构 | 视影响范围 | 更新计划 |

### 提交前检查

- [ ] 新增 `.md` 文件有 frontmatter
- [ ] 链接目标存在
- [ ] `dev/progress/status.md` 反映最新状态

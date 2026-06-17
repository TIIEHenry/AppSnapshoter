---
title: "文档系统概述"
type: guide
status: active
updated: 2026-06-17
summary: "文档系统的组织结构、维护规则和使用说明"
---

# 文档系统概述

AppSnapshoter 的文档分为两层：**知识层** (`docs/`) 和 **行动层** (`dev/`)。

## 目录结构

```
docs/                           # 知识层 — 稳定的、跨模块的知识
├── INDEX.md                    # 全局文档索引（手动维护）
├── README.md                   # 本文件
├── DOCS-SPEC.md                # 文档规范
├── glossary.md                 # 术语表
├── architecture/               # 架构文档（全局视角）
│   ├── overview.md             # 系统架构全景
│   ├── compression-pipeline.md # 压缩管线架构
│   ├── root-service.md         # Root 服务架构
│   └── cross-cutting/          # 横切关注点
│       ├── security.md         # 安全策略
│       └── storage.md          # 存储策略
├── systems/                    # 系统文档（跨模块功能域）
│   ├── INDEX.md                # 系统文档索引
│   ├── snapshot/               # 快照系统
│   │   └── INDEX.md
│   ├── compression/            # 压缩系统
│   │   └── INDEX.md
│   ├── timeline/               # 时间线系统
│   │   ├── INDEX.md
│   │   └── TIMELINE_FEATURE.md # 时间线设计文档索引
│   └── config/                 # 配置系统
│       └── INDEX.md
├── modules/                    # 模块导航索引（每个 Gradle 模块一个）
│   ├── app/INDEX.md
│   ├── api/INDEX.md
│   ├── provider/INDEX.md
│   ├── hiddenapi/INDEX.md
│   ├── systemapi/INDEX.md
│   ├── io-nativefs/INDEX.md
│   ├── io-tar/INDEX.md
│   └── io-zstd/INDEX.md
├── guides/                     # 用户面向文档
│   └── getting-started/
│       ├── INDEX.md
│       ├── quickstart.md
│       ├── build.md
│       ├── syncthing.md
│       └── troubleshooting.md
├── templates/                  # 文档模板
│   ├── decision-template.md    # ADR 模板
│   ├── plan-template.md        # 实施计划模板
│   └── feature-template.md     # 功能设计模板
├── screenshots/                # 截图资源
└── timeline/                   # 时间线设计子文档（01-08）

dev/                            # 行动层 — 频繁更新的开发追踪
├── README.md                   # 目录说明
├── progress/                   # 当前状态
│   └── status.md
├── plans/                      # 实施计划
│   └── overview.md
├── decisions/                  # 架构决策记录（ADR）
│   ├── INDEX.md
│   └── 001-two-layer-doc-system.md
└── roadmap/                    # 路线图
    ├── active/                 # 当前阶段
    └── archive/                # 已完成阶段
```

## 维护规则

### 知识层 (`docs/`)

- 代码行为/契约/UX **变更时**更新
- 稳定性优先：不因小改动频繁修改
- 所有 `.md` 文件需 YAML frontmatter（`INDEX.md`、`README.md` 除外可省略）

### 行动层 (`dev/`)

- **每次含代码变更的提交前**更新 `dev/progress/status.md`
- ADR 在做出架构决策时创建
- 计划在规划新功能/重构时创建

### Frontmatter 格式

```yaml
---
title: "文档标题"
type: architecture | system | module | guide | decision | plan | progress | index | template
status: draft | active | completed | archived
updated: YYYY-MM-DD
summary: "一句话描述，用于搜索和健康检查"
---
```

### 命名规范

| 类型 | 格式 | 示例 |
|------|------|------|
| 文件 | `kebab-case.md` | `compression-pipeline.md` |
| 目录 | 小写 | `snapshot/`, `timeline/` |
| ADR | `NNN-topic.md` | `001-use-zstd-compression.md` |
| 计划 | `phase-N-topic.md` | `phase-1-timeline-mvp.md` |

## 双向链接约定

- `docs/modules/X/INDEX.md` 引用对应模块的 `README.md`（如有）
- 模块根目录的 `README.md` 引用 `docs/modules/X/INDEX.md`
- 系统文档引用相关模块，模块 INDEX 引用相关系统

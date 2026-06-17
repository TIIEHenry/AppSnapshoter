---
title: "开发追踪目录说明"
type: guide
status: active
updated: 2026-06-17
summary: "dev/ 目录的组织结构和使用说明"
---

# 开发追踪目录 (dev/)

`dev/` 是行动层，追踪开发过程中的决策、计划和进度。与 `docs/`（知识层）互补。

## 目录结构

```
dev/
├── README.md           # 本文件
├── progress/           # 当前开发状态
│   └── status.md       # 每次含代码变更的提交前更新
├── plans/              # 实施计划
│   ├── overview.md     # 所有阶段概览、当前焦点
│   └── phase-N-*.md    # 具体阶段计划
├── decisions/          # 架构决策记录（ADR）
│   ├── INDEX.md        # 决策索引
│   └── NNN-topic.md    # 单个决策记录
└── roadmap/            # 路线图
    ├── active/         # 当前阶段任务清单
    └── archive/        # 已完成阶段
```

## 维护规则

### progress/status.md

- **每次含代码变更的提交前**必须更新
- 记录当前迭代状态、进行中的功能、已知问题

### plans/

- 规划新功能或重构时创建
- 使用 [计划模板](../docs/templates/plan-template.md)

### decisions/

- 做出架构决策时创建 ADR
- 使用 [ADR 模板](../docs/templates/decision-template.md)
- 编号递增：`001-topic.md`、`002-topic.md`...

### roadmap/

- `active/` 存放当前阶段任务清单
- 完成后移入 `archive/`
- 只归档，不删除

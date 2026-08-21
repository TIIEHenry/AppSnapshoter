---
title: "分组 body 三态可见性 — 根因与修复"
type: system
status: implemented
updated: 2026-08-21
summary: "expand_group / empty_layout / app_layout 互斥；空组始终加号、忽略折叠；有应用才区分折/展"
---

# 分组 body 三态可见性 — 根因与修复

[← 返回快照系统索引](INDEX.md)

## 0. 结论摘要

| 项 | 内容 |
|----|------|
| 症状（历史） | 空组折叠后可同时显示加号与展开箭头 |
| 问题类 | **状态机缺口** — 折叠轴与空组轴各自改 visibility |
| 选定设计 | 单一 `renderBody(group)`；**空组优先于折叠** |
| 当前不变量 | 空组始终仅 `empty_layout`；有应用才投影折/展 |

---

## 投影规则（现行）

```text
apps.isEmpty()              → EMPTY：仅 empty_layout（忽略 isCollapsed）
!empty && isCollapsed       → COLLAPSED：仅 expand_group
else                        → CONTENT：仅 app_layout
```

展开后的应用网格：4 列；超过 **3 行**（12 个）后高度封顶，组内网格自己滚动，不再撑开外层存档列表。不足 3 行仍 `wrap_content`。可滚动时顶/底渐隐；竖滑先给组内，滑到尽头再交给外层列表。

标题点击：空组不切换 `isCollapsed`；有应用才折叠/展开。

`btnAdd`：空组 toolbar 加号恒 GONE；入口仅 `empty_layout`。

### 验证

1. 空组：仅加号；点标题 → 仍仅加号（无箭头）
2. 有应用折叠 → 仅箭头；展开 → 仅网格
3. 空组添加首个应用 → 加号消失、网格出现
4. 点刷新 → 从不双图标

### 触点

| 文件 | 改动 |
|------|------|
| `GroupsAdapter.kt` | `renderBody`：空优先；组内网格超过 3 行封顶自滚 |
| `GroupActionsController.kt` | 空组标题点击不切换折叠 |
| `MaxHeightRecyclerView.kt` | 高度上限、渐隐、竖滑交接 |

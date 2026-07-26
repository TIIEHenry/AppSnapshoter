---
title: "分组 body 三态可见性 — 根因与修复"
type: system
status: implemented
updated: 2026-07-26
summary: "expand_group / empty_layout / app_layout 由两套独立逻辑驱动，空组折叠或刷新后可同时显示加号与展开箭头；收敛为单一 render(group) 投影"
---

# 分组 body 三态可见性 — 根因与修复

[← 返回快照系统索引](INDEX.md)

## 0. 结论摘要

| 项 | 内容 |
|----|------|
| 症状 | 分组区域同时出现「加号」与「展开箭头」，点刷新后更明显 |
| 问题类 | **状态机缺口 / 缺少不变量** — 折叠轴与空组轴各自改 visibility，无互斥契约 |
| 选定设计 | ViewHolder 单一入口 `renderBody(group)`，按 `isCollapsed` → 空 → 有内容 投影；`btnAdd` 空态纳入同源规则 |
| Composer 审查 | Approve with changes（已并入：删 bind 前置半更新、`btnAdd` 第二轴、Controller 尽量零改） |
| 复发机制 | 任何新调用点只改其中一轴，就会再次叠出双图标 |

修完后：body 三 sibling 与空组 toolbar 加号只经 `renderBody`；单入口 + KDoc 契约降低同类复发。

---

## 1. 现象与复现

`item_group.xml` 中三个 sibling 叠在同一 `FrameLayout`：

| View | 含义 |
|------|------|
| `expand_group` | 折叠态：chevron 展开 |
| `empty_layout` | 空组：加号入口 |
| `app_layout` | 有应用：网格 |

**复现**：

1. 空分组 → 显示加号（正确）
2. 点击分组标题折叠 → `updateCollapseState(true)` 把 `expand_group` 设为 VISIBLE，但 **不** 隐藏 `empty_layout` → **加号 + 箭头并存**
3. 点刷新：`refresh()` 先按 `apps.isEmpty()` 显示 `empty_layout`，再调 `updateCollapseState(isCollapsed)`；若仍为折叠，再次叠出双图标

有应用且折叠时单独看 `updateCollapseState` 正常；问题集中在 **空 × 折叠** 交叉态。

---

## 2. 问题类

**标签**：状态机缺口 + 可见性不变量散落在调用点。

当前两套逻辑正交叠加：

```text
refresh()            → emptyLayout ↔ recycler（空 / 非空）
updateCollapseState() → expandGroup ↔ appLayout（折 / 展）
```

缺失不变量：

> 任意时刻，`expand_group` / `empty_layout` / `app_layout` **至多一个** VISIBLE。

为何会复发：标题折叠、刷新、`bind`、添加应用回调各自半更新；新入口很容易只改一轴。

最小补丁（例如「折叠时顺便 GONE empty」）仍把规则散落在多处，半年后同类 bug 仍易出现。

---

## 3. 方案对比

### Option A — 调用点补丁

在 `updateCollapseState` / `refresh` 各加几行互斥 `if`。

- Pros: 改动面极小
- Cons: 不变量仍双入口；标题折叠不经过 `refresh` 时易漏
- Prevents recurrence?: **no**

### Option B — 单一状态投影（选定）

抽出 `applyGroupBodyState(isCollapsed: Boolean, isEmpty: Boolean)`：

```text
isCollapsed     → COLLAPSED：仅 expand_group
!collapsed && empty → EMPTY：仅 empty_layout
else            → CONTENT：仅 app_layout（再设 recycler 可见）
```

`refresh`、`updateCollapseState`、标题/展开点击全部委托该函数。`btnAdd` 在空组仍由现有逻辑隐藏（empty 区代入口），不并入三态亦可。

- Pros: 单一真相源；交叉态有明确优先级（折叠优先于空）
- Cons: 多一个小函数
- Prevents recurrence?: **yes** — 新逻辑只能改投影或传参

### Option C — 引入 sealed `GroupBodyUiState` 数据类 + LiveData

- Pros: 类型更强
- Cons: 对本 UI 局部问题过重；状态已在 `SnapGroup.isCollapsed` + `apps`
- Prevents recurrence?: yes，但过度设计

**拒绝 A**：治标。**拒绝 C**：复杂度无收益。**选定 B**。

---

## 4. 设计决策

| 决策 | 说明 |
|------|------|
| 折叠优先 | 空组也可折叠；折叠时只显示箭头，展开后再显示加号 |
| 不改 MMKV / `isCollapsed` 语义 | 仅修 UI 投影 |
| 不改布局结构 | 仍用 FrameLayout 三 sibling；靠代码互斥 |
| 触点 | `GroupsAdapter.renderBody` + `syncChromeVisibility`；Controller 折叠点击仍调 `updateCollapseState`，内部经 `boundGroup` 全量投影 |
| `btnAdd` | 空组 toolbar 加号恒 GONE；加号入口仅 `empty_layout` |

### 非目标

- 不改 DiffUtil / `loadGroups` / stale 实例问题（已有文档）
- 不禁止空组折叠
- 不引入 Compose / 新模块
- Controller 不承担 body 三态规则（只改 `isCollapsed` 再调 ViewHolder）

### 验证

1. 空组：仅加号；点标题折叠 → 仅箭头；再点箭头 → 仅加号
2. 有应用折叠 → 仅箭头；展开 → 仅网格
3. 空组 / 有应用组点刷新 → 从不双图标
4. 空组添加首个应用 → 加号消失、网格出现
5. 空组进入/退出排序模式 → 仍仅一处加号（empty_layout）

### 文档

- 本页；INDEX 已链入；`status: implemented`

---

## 5. 触点（已实施）

| 文件 | 改动 |
|------|------|
| `GroupsAdapter.kt` | `boundGroup`；`renderBody(group)` 三态互斥；`refresh` / `updateCollapseState` 委托；删 bind 前置半更新；`syncChromeVisibility` |
| `GroupActionsController.kt` | `updateButtonVisibility(show, isEmpty)` — 空组不显示 toolbar `btnAdd` |
| `docs/systems/snapshot/INDEX.md` | 链入本页 |

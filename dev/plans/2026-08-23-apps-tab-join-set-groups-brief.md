# OpenCode brief: 应用 Tab「加入」支持集内组

## 背景

v1 已落地（main @ 2c1b80e）：应用 Tab 长按 popup，上排「加入」仅可选**独立组**（`GroupCard.setId == null`）。

用户真机反馈：希望「加入」时也能选**分组集内的组**。

## 目标

扩展「加入」选组 Dialog，候选包含：
1. 独立组（`setId == null`）— 保持现有行为
2. 集内组（`setId != null`）— **新增**

展示建议（方案 Phase 2）：扁平列表，标签 `集名 / 组名`（或 `组名（集名）`），按 `archiveList` 顺序。

## 约束（必须遵守）

- 写入仍只走 `SnapshotViewModel.addAppsToGroup` + `AddAppsResultUi`（含 conflict move）
- 候选仍从 `archiveList` 的 `GroupCard` 投影，禁止用 path 猜测
- 过滤：`group.userId == app.userId`；排除已含该 package 的组（`!containsPackage`）
- 选组后：先 `dismiss()` popup，再 `addAppsToGroup`
- 不 weakening repository 不变量
- i18n：三 locale strings.xml
- 先读 `AGENTS.md`、`docs/systems/snapshot/APPS_TAB_ITEM_POPUP.md`、`GROUP_SET.md`
- Kotlin + ViewBinding，不加 Compose
- 为 resolver 新逻辑加/扩 unit test
- 更新 `APPS_TAB_ITEM_POPUP.md` Phase 2 勾选与 D9 勘误
- 编译 + 相关单测通过
- **不要** commit，除非用户明确要求

## 关键文件

- `app/.../group/GroupMembershipResolver.kt` — 新增如 `joinTargets(cards, pkg, userId)` 替代或扩展 `independentJoinTargets`
- `app/.../main/apps/AppsItemPopupMenu.kt` — `showJoinGroupPicker` 用新候选 + 展示名
- `app/.../group/GroupMembershipResolverTest.kt`
- `res/values{,-zh-rCN,-en}/strings.xml` — 如需新文案
- `docs/systems/snapshot/APPS_TAB_ITEM_POPUP.md`

## 参考

`JoinTargetCard(group, setId, userId)` 已有；集名可从 `snapshotViewModel` 的 group set 列表或 card 上下文解析。

## 验收

- 有集内组时，加入 Dialog 可见且可选
- 选集内组成功加入（无冲突）或走 conflict 移动
- 独立组仍可选
- 跨 user / 已成员组仍不可选

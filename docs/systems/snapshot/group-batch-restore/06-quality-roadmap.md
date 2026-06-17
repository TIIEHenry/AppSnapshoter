---
title: "Group 批量恢复 — 边界、测试与实施计划"
type: system
status: draft
updated: 2026-06-17
summary: "边界情况、性能与测试范围、Phase 划分与验收标准"
---

# 边界 / 测试 / 实施计划

[← 返回索引](../GROUP_BATCH_RESTORE.md)

---

## 6.1 边界情况

| 场景 | 预期行为 |
|------|----------|
| 组内无快照 | 预览 0；Toast「没有可恢复的存档」 |
| 范围「未安装」但全部已安装 | 预览 0；开始恢复 disabled |
| 「自上次恢复以来」全部已恢复且无新快照 | 预览 0 |
| `LAST_RESTORED` 但无历史记录 | 回退最新；预览脚注说明 |
| 记录中的 `archiveName` 已被删除 | 回退最新；若恢复仍失败则进 failed 列表 |
| 应用正在运行 | v1 建议恢复前 `forceStopPackage`（当前 `restoreArchive` 仅 `clearAppData`） |
| 磁盘空间不足 | 该项失败，继续下一项 |
| 批量进行中用户触发单应用恢复 | `isBatchRunning` 阻止 |
| 批量归档与批量恢复同时触发 | 互斥，后触发者 Toast 提示 |
| 多用户 / 工作配置文件 | `userId` 来自 `SnapGroup.userId`，与 `AppRestoreKey` 一致 |
| 锁定快照 | 不阻止恢复 |
| 取消批量 | 当前 app 完成后停止；未执行项不写 `RestoreRecord` |
| 执行中 `loadGroups()` | 每项前从 `groupList` 重查 `ArchivedApp`，避免 stale |

---

## 6.2 性能

| 项 | 方案 |
|----|------|
| 执行模型 | **串行**恢复；root 解压 + chown 不宜并行 |
| 计划构建 | 纯内存过滤，`O(组内应用数 × 快照数)`，对话框内实时计算可接受 |
| MMKV 读取 | 打开对话框时 `loadAll` 一次；不必逐项读 |
| UI 线程 | 计划构建在 Main；执行在 `Dispatchers.IO` |
| 组头布局 | 32dp 按钮降低单行宽度压力；标题独立行避免 measure 抖动 |

---

## 6.3 测试范围

### 单元测试（`GroupBatchRestorePlanner`）

| 用例 | 断言 |
|------|------|
| `ALL` + `NEWEST` | 返回全部有快照 app；archive 为 makeTime 最大 |
| `NOT_INSTALLED` | 仅未安装且有快照的 app |
| `SINCE_LAST_RESTORE` + 无 record | 纳入该 app |
| `SINCE_LAST_RESTORE` + 有新快照 | 纳入 |
| `SINCE_LAST_RESTORE` + 无新快照 | 排除 |
| `LAST_RESTORED` + 有 record 且 archive 存在 | 选中同名 archive |
| `LAST_RESTORED` + archive 已删 | 回退 NEWEST，`fallbackCount++` |

### 仪器测试 / 手工

| 用例 | 步骤 |
|------|------|
| 组头布局 | 长组名省略；320dp / 360dp / 412dp 宽度无按钮溢出 |
| 批量菜单 | 归档路径不变；恢复打开配置框 |
| 预览联动 | 切换 Radio 后数量正确 |
| 未安装恢复 | 范围选未安装 → APK 安装 + 数据恢复 |
| 增量范围 | 批量恢复后新归档 → 「自上次恢复以来」仅命中新备份 app |
| 取消 | 中途取消后已完成项有 record，未完成项无 record |
| 失败继续 | 故意损坏某 archive → 其余仍执行；失败列表可查 |

---

## 6.4 实施计划

### Phase 1：基础设施（约 1 天）

- [ ] `RestoreRecord` + `RestoreRecordStore`
- [ ] `GroupBatchRestorePlanner` + 单元测试
- [ ] `ArchivePickStrategy` / 共享 `resolveArchive`
- [ ] `ArchiveRestorer` 成功路径写 record

### Phase 2：UI（约 1 天）

- [ ] `item_group.xml` 双行布局 + `btn_batch`
- [ ] `GroupActionsController` 批量菜单
- [ ] `dialog_group_batch_restore.xml` + `GroupBatchRestoreDialog`
- [ ] `strings.xml` 文案

### Phase 3：执行与集成（约 1 天）

- [ ] `GroupBatchRestorer`
- [ ] `LauncherViewModel.isBatchRunning` + UI 互斥
- [ ] 与时间线批量互斥（可选）
- [ ] 恢复前 `forceStopPackage`（建议纳入 Phase 3）

### Phase 4：v2 增强（可选）

- [ ] 记住上次对话框选项（group MMKV）
- [ ] 提取 `BatchProgressDialogs` 共用组件
- [ ] 溢出菜单 `btn_more`（若真机验证仍拥挤）

---

## 验收标准

- [ ] 组头在常见屏宽下标题与按钮均正常显示，无重叠溢出
- [ ] 「全部归档」功能与改版前行为一致
- [ ] 批量恢复对话框可配置 **三种范围 × 三种快照策略**
- [ ] 预览数量与实执行数量一致
- [ ] 串行进度、取消、成功/失败汇总可用
- [ ] 单应用恢复与批量恢复均正确写入 `RestoreRecord`
- [ ] 「自上次恢复以来」仅命中从未恢复或有新快照的应用
- [ ] 「与上次相同」在无记录或快照缺失时回退最新且不崩溃
- [ ] 批量进行中无法重复触发归档/恢复

---

## 6.5 风险与缓解

| 风险 | 缓解 |
|------|------|
| 恢复中应用未 force stop 导致失败 | Phase 3 增加 force stop |
| 组头改版影响排序模式 | `updateButtonVisibility` 一并测试自定义排序 |
| `RestoreRecord` 与跨设备同步 | 存 group MMKV / 组目录均可被 Syncthing 同步；需在文档注明「恢复记录会随组目录同步」 |
| 与时间线 `RestoreStrategy` 命名混淆 | Group 专用 `ArchivePickStrategy`；文档与代码注释区分 |

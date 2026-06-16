# 边界情况、性能与测试

[← 返回索引](../TIMELINE_FEATURE.md)

---

## 边界情况

| 场景 | 处理方式 |
|------|----------|
| 时间区域内无快照 | 空状态：「该时间范围内没有备份快照」 |
| 应用未安装 | 走现有 `ArchiveRestorer` 逻辑（安装 APK + 恢复数据） |
| 应用正在运行 | 沿用现有恢复前检查/提示 |
| 锁定快照 | 删除跳过并计入汇总；恢复允许 |
| 筛选变更时有选中项 | 清空选中并退出多选 |
| 恢复/删除进行中 | `isBatchRunning` 禁用操作栏，防重复提交 |
| 跨用户同包名 | `TimelineEntryKey` 含 `userId` |
| 操作后数据一致性 | `loadGroups()` → `groupList` 观察触发 requery |
| groupList 未加载 | loading 占位，`loadGroups()` 完成后自动展示 |
| 操作中 entry 被删光 | `resolveEntry` 返回 null → 计为失败，继续下一项 |
| `makeTime` 缺失/为 0 | 视为 0，仍参与筛选（与现有排序行为一致） |

---

## 性能

| 点 | 方案 |
|----|------|
| 查询 | `Dispatchers.Default`，典型规模（数十组 × 数百应用 × 数个快照）< 50ms |
| 列表更新 | `ListAdapter` + `DiffUtil`，payload 区分选中态 |
| 重复 query | `timeRange` 未变且 `groupList` 引用相同时可跳过（可选优化） |
| 大分组（二期） | 仅在实测 > 200ms 时考虑 `(makeTime, key, archiveName)` 索引 |

首版不做索引缓存，避免过早优化。

---

## 测试

| 类型 | 范围 |
|------|------|
| 单元测试 | `TimelineRepository.query` — 边界时间、空组、多快照排序、跨组同包名 |
| 单元测试 | `resolveArchive` 策略选取 |
| 手动验收 | 见 [验收标准](07-roadmap.md#验收标准) |

测试数据：构造内存中的 `SnapGroup` / `ArchivedApp` / `ArchiveItem`，无需 Root 设备。

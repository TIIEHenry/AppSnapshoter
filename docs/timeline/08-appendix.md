# 附录：与现有代码集成点

[← 返回索引](../TIMELINE_FEATURE.md)

---

```kotlin
// Fragment 获取 SnapshotViewModel
private val snapshotViewModel: SnapshotViewModel by activityViewModels {
    SingletonViewModelFactory(SnapshotApp.getViewModel())
}
private val timelineViewModel: TimelineViewModel by activityViewModels()

// 恢复（新增公共方法）
ArchiveRestorer.restoreArchiveItem(context, archivedApp, archiveItem, updateCurrent, scope)

// 删除
ArchiveManager.deleteArchive(archivedApp, archiveItem)

// 数据源绑定
snapshotViewModel.groupList.observe(viewLifecycleOwner) { groups ->
    timelineViewModel.onGroupsUpdated(groups)
}

// 多选 UI 参考
// SelectAppFragment + SelectAppAdapter + layout_multi_select_toolbar.xml
// TimelineAdapter：单击与长按均绑 item_content（勿仅绑 root Card，否则长按无响应）

// 批量进度参考（TimelineBatchOperator 应结构对齐）
// GroupBatchArchiver.archiveAllApps() + GroupItemsProgressDialog
```

## v1.0 → v1.1 变更摘要

| 项 | v1.0 | v1.1 |
|----|------|------|
| Entry 模型 | 持有 `SnapGroup`/`ArchivedApp` 引用 | 仅 `TimelineEntryKey` + 展示缓存，操作前 resolve |
| 批量类 | `TimelineBatchRestorer` + `TimelineBatchDeleter` | 合并为 `TimelineBatchOperator` |
| 状态 | LiveData + StateFlow 混用 | 统一 LiveData |
| 时间边界 | 闭区间 `..endTime` | 左闭右开 `until endTimeExclusive` + `java.time` |
| 恢复进度 | 未明确 | 批量用 `GroupItemsProgressDialog`，不用逐条 `ItemProgressDialog` |
| 待确认项 | 4 条开放问题 | 移入 [已定设计决策](01-overview.md#14-已定设计决策) |
| 工期 | 3~4 天 | 2~3 天（减文件、对齐现有模式） |

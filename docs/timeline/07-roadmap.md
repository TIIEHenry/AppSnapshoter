# 实施计划与验收标准

[← 返回索引](../TIMELINE_FEATURE.md)

---

## 实施计划

### Phase 1 — MVP（约 2~3 天）

- [x] 导航 + `TimelineFragment` 骨架
- [x] `TimelineModels` + `TimelineRepository.query` + 单元测试
- [x] `TimelineViewModel` 绑定 `groupList` + Chip 筛选（今天/昨天/7天/30天）
- [x] 列表展示 + DiffUtil
- [x] 多选工具栏（复用 `layout_multi_select_toolbar`）
- [x] `ArchiveRestorer.restoreArchiveItem()` 提取
- [x] `TimelineBatchOperator` 批量删除（含锁定跳过）
- [x] `TimelineBatchOperator` 批量恢复 + 策略对话框

### Phase 2 — 体验增强

- [x] 自定义日期范围（Material DateRangePicker）
- [x] 筛选 preset MMKV 持久化
- [x] 点击条目跳转存档 Tab 对应分组/应用（Deep link 或 SharedFlow 事件）
- [x] 展开查看时间区域内快照明细
- [x] 按分组名 / 应用名搜索过滤

### Phase 3 — 扩展

- [x] 批量导出
- [x] 时间线图表视图（日历热力图）

---

## 验收标准

- [x] 底部导航可见「时间线」Tab，顺序为 `存档 | 时间线 | 应用`
- [x] 可选择时间区域，列表仅展示该区域内有快照的应用
- [x] 列表项正确显示应用名、分组、快照数、时间摘要
- [x] 支持长按列表项进入多选，全选 / 取消可用
- [x] 批量恢复：多快照应用弹出策略选择；串行进度；失败可查看汇总
- [x] 批量删除：确认后删除区域内全部匹配快照；锁定跳过并有提示
- [x] 操作完成后列表自动刷新，存档 Tab 数据同步
- [x] 批量操作过程中不可重复触发

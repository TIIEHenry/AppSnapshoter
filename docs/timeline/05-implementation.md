# 模块与文件结构

[← 返回索引](../TIMELINE_FEATURE.md)

---

## 5.1 新增文件（精简后 7 个 Kotlin + 3 布局）

```
app/src/main/java/tiiehenry/android/app/snapshot/main/timeline/
├── TimelineFragment.kt           # Tab 主界面、Chip 筛选、多选工具栏
├── TimelineViewModel.kt          # timeRange / entries / selection / query 调度
├── TimelineRepository.kt         # 纯函数 query，便于单测
├── TimelineModels.kt             # EntryKey, Entry, TimeRange, RestoreStrategy
├── TimelineAdapter.kt            # RecyclerView + DiffUtil + 多选
├── TimelineBatchOperator.kt      # 批量恢复/删除编排（对标 GroupBatchArchiver）
└── RestoreStrategyDialog.kt      # 策略选择

app/src/main/res/layout/
├── fragment_timeline.xml
├── item_timeline_entry.xml
├── item_timeline_date_header.xml
├── layout_search_field.xml         # 共享搜索框（Dense Outlined）
└── dialog_restore_strategy.xml   # 或用 MaterialAlertDialogBuilder 内联，二选一

app/src/main/java/tiiehenry/android/app/snapshot/ui/widget/
├── CollapsibleSearchController.kt  # 可折叠搜索（Chip 行图标 ↔ 输入框）
└── SearchFieldExt.kt               # TextInputLayout 文本监听扩展

app/src/main/res/drawable/
└── tab_timeline.xml
```

> v1.0 中的 `TimelineBatchRestorer` + `TimelineBatchDeleter` 合并为 `TimelineBatchOperator`；`TimelineEntry.kt` + `TimeRange.kt` 合并为 `TimelineModels.kt`。

## 5.2 修改文件

| 文件 | 变更 |
|------|------|
| `bottom_nav_menu.xml` | 新增「时间线」菜单项（位于存档与应用之间） |
| `nav_graph.xml` | 新增 `timelineFragment` 节点 |
| `ArchiveRestorer.kt` | 新增 `restoreArchiveItem()` / `restoreArchiveSuspend()` 公共入口 |
| `SnapshotViewModel.kt` | 新增 `navigateToGroup` 事件，供时间线跳转存档 Tab |
| `LauncherFragment.kt` | 监听 `navigateToGroup`，滚动到对应分组 |
| `GlobalConfig.kt` | 时间线筛选 preset 与自定义日期 MMKV 持久化 |
| `SnapGroup.kt` | `name` 空值回退，避免标签筛选等场景 NPE |
| `IFileSystem.java` / `FileSystemImpl.kt` | 新增 `copyRecursively()`（批量导出） |
| `strings.xml` | 时间线相关文案 |
| `fragment_apps.xml` / `fragment_select_app.xml` / `fragment_ignore_apps.xml` | 与 Timeline 相同的 Chip 行 + 可折叠搜索 |
| `values/dimens.xml` | `filter_horizontal_padding`、`filter_icon_button_size` 等筛选区尺寸 |
| `values/themes.xml` | `Widget.AppSnapshot.SearchField`、`Widget.AppSnapshot.IconButton` |

单元测试：`app/src/test/.../TimelineRepositoryTest.kt`

## 5.3 ViewModel 状态

与项目现有风格对齐，**统一使用 LiveData**（不引入 StateFlow）：

```kotlin
class TimelineViewModel : ViewModel() {

    val timeRange = MutableLiveData(defaultLast7Days())
    val entries = MutableLiveData<List<TimelineEntry>>(emptyList())
    val selectedIds = MutableLiveData<Set<String>>(emptySet())
    val isMultiSelectMode = MutableLiveData(false)
    val isQuerying = MutableLiveData(false)
    val isBatchRunning = MutableLiveData(false)
    val searchQuery = MutableLiveData("")   // 搜索词，驱动 Adapter 高亮与过滤

    fun bindGroupList(groupList: LiveData<List<SnapGroup>>) {
        // 观察 groupList，与 timeRange 任一变化时在 Default 线程 requery
    }

    fun setTimeRange(range: TimeRange) {
        timeRange.value = range
        clearSelection()
    }

    fun toggleSelection(id: String) { ... }
    fun selectAll() { selectedIds.value = entries.value.orEmpty().map { it.key.id }.toSet() }
    fun clearSelection() { selectedIds.value = emptySet(); isMultiSelectMode.value = false }
}
```

`TimelineViewModel` 通过标准 `by activityViewModels()` 创建（同 `LauncherViewModel`）。  
`TimelineFragment` 注入 `SnapshotViewModel`（Singleton 工厂）+ `TimelineViewModel`。

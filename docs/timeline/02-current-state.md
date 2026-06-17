# 现状分析

[← 返回索引](../TIMELINE_FEATURE.md)

---

## 2.1 数据模型

```
SnapGroup (分组)
  └── ArchivedApp (应用快照目录)
        └── ArchiveItem (单个快照)
              └── MetaInfo.makeTime  ← 快照创建时间（毫秒时间戳）
```

关键类：

| 类 | 路径 | 说明 |
|----|------|------|
| `SnapGroup` | `group/SnapGroup.kt` | 分组，含 `apps: MutableList<ArchivedApp>`；`name` 优先读 `group.json`，缺失时回退为目录名或 `id` |
| `ArchivedApp` | `group/ArchivedApp.kt` | 应用级快照容器，`archives: LinkedHashMap<String, ArchiveItem>` |
| `ArchiveItem` | `archive/ArchiveItem.kt` | 单个快照，含 `metaInfo.makeTime` |
| `MetaInfo` | `archive/bean/MetaInfo.java` | 快照元信息，`makeTime` 字段 |

## 2.2 数据加载链路

```
SnapshotApp.onCreate()
  → SnapshotViewModel.loadGroups()
    → AppDataRepository.loadGroups()
      → SnapGroup.loadApps() → ArchivedApp.loadArchives()
```

全局数据源：`SnapshotViewModel.groupList: LiveData<List<SnapGroup>>`

Fragment 获取方式（与 `LauncherFragment` 一致）：

```kotlin
private val snapshotViewModel: SnapshotViewModel by activityViewModels {
    SingletonViewModelFactory(SnapshotApp.getViewModel())
}
```

## 2.3 可复用能力

| 能力 | 现有实现 | 复用方式 |
|------|----------|----------|
| 单快照恢复 | `ArchiveRestorer.restoreLatest()` | 提取 `restoreArchiveItem()` 后，批量按指定 `ArchiveItem` 调用 |
| 单快照删除 | `ArchiveManager.deleteArchive()` | 批量删除循环调用 |
| 多选 UI | `SelectAppFragment` + `layout_multi_select_toolbar.xml` | include 工具栏 + 隐藏 `confirm_button`，另加恢复/删除按钮 |
| 批量进度 | `GroupBatchArchiver` + `GroupItemsProgressDialog` | **直接照搬**串行执行 + 取消 + 成功/失败汇总模式 |
| 锁定保护 | `MetaInfo.locked` | 删除时跳过已锁定快照 |

## 2.4 导航结构

- 菜单：`app/src/main/res/menu/bottom_nav_menu.xml`
- 导航图：`app/src/main/res/navigation/nav_graph.xml`
- 宿主：`MainActivity` + 悬浮 `BlurView` 底栏（`FloatingBottomNav`）
- 顶栏：固定紧凑 `MaterialToolbar`（`toolbar_height` 48dp），标题随 Tab 切换，设置入口在 `menu_main.xml`
- 布局：`activity_main.xml`（`ConstraintLayout`，内容区在顶栏下方，不重叠）

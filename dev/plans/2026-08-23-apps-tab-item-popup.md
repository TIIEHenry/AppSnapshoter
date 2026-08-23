---
title: "应用 Tab Item 长按 Popup 实施计划"
type: plan
status: implemented
updated: 2026-08-23
summary: "按 APPS_TAB_ITEM_POPUP 落地：popup 壳、归属组列表、上排「加入」选独立组、详情/卸载、抽出冲突 UI"
---

# 应用 Tab Item 长按 Popup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 应用 Tab 长按改为存档 Item 同构 popup：下半列出已加入的组并可跳转；上排一个「加入」按钮选独立组；另有系统信息 / 应用配置 / 卸载。

**Architecture:** 新 UI 只读 `archiveList` / `groupList` 并调用已有 `addAppsToGroup` / `moveAppBetweenGroups`。冲突对话框从 `GroupActionsController` 整段抽出（含 `moveApps`）。独立组候选是纯函数。卸载走 repository `scope` + 已有 `scheduleLoadApps`，不 `loadData`。

**Tech Stack:** Kotlin, ViewBinding, PopupWindow, LiveData, JUnit4 unit tests, `./gradlew test`

**Spec:** [docs/systems/snapshot/APPS_TAB_ITEM_POPUP.md](../../docs/systems/snapshot/APPS_TAB_ITEM_POPUP.md)

## Global Constraints

- 不变量仍由 `AppDataRepository.addAppsToGroup` / `moveAppBetweenGroups` 强制；UI 禁止另开写入口
- 独立组 = `ArchiveListItem.GroupCard.setId == null`；加入候选禁止 fallback 集内组
- 组行跳转只许 `SnapshotViewModel.requestNavigateToGroup`
- 任何 `addAppsToGroup` 调用前先 dismiss popup
- catalog 刷新只许 `SnapshotViewModel.loadApps()`（已存在，转发 `repository.scheduleLoadApps`）；禁止卸载后 `loadData()`
- `AppDataRepository.scope` 是 private；新后台工作加 repository 方法，禁止 `SnapshotViewModel.viewModelScope` 做数据加载
- 用户文案进 `values` / `values-zh-rCN` / `values-en`，前缀 `apps_popup_`
- 不改存档 Tab popup 行为（只把系统信息改调抽出函数）
- 单击应用 Item 仍打开 `AppConfigFragment`
- **已存在、不要再加：** `AppDataRepository.scheduleLoadApps`、`SnapshotViewModel.loadApps()`（方案 D16 文件表过时）

## File map

| 文件 | 任务 | 职责 |
|------|------|------|
| `group/GroupMembershipResolver.kt` | 1 | `JoinTargetCard`、`membershipRows`、`independentJoinTargets` |
| `app/src/test/.../GroupMembershipResolverTest.kt` | 1 | 候选与组行顺序 |
| `utils/AppDetailsLauncher.kt` | 2 | 系统应用信息 |
| `launch/popup/ArchiveItemPopupMenu.kt` | 2 | 改调 launcher |
| `group/AddAppsResultUi.kt` | 3 | handle + moveApps |
| `launch/GroupActionsController.kt` | 3 | 委托 AddAppsResultUi |
| `res/values*/strings.xml` | 4 | `apps_popup_*` |
| `res/layout/layout_apps_popup_menu.xml` | 4 | 四键 + 列表 |
| `res/layout/item_apps_popup_group.xml` | 4 | 组行 |
| `main/apps/AppsPopupGroupAdapter.kt` | 5 | 组行 adapter |
| `main/apps/AppsItemPopupMenu.kt` | 5 | popup 壳 |
| `main/apps/AppsAdapter.kt` | 6 | 长按带 View |
| `main/apps/AppsFragment.kt` | 6 | 接线；删 dialog |
| `main/apps/AppMembershipDialog.kt` | 6 | 删除 |
| `main/apps/AppsViewModel.kt` | 7 | `refreshMembershipFilter()` |
| `main/apps/AppsListComponent.kt` | 7 | 观察 `groupList` |
| `repository/AppDataRepository.kt` | 8 | `uninstallInstalledApp` |
| `group/UninstallAppResult.kt` | 8 | 结果类型 |
| `SnapshotViewModel.kt` | 8 | `uninstallApp` 门面 |
| 方案 / INDEX / overview | 9 | 状态与 D16 勘误 |

---

### Task 1: Resolver 纯函数 + 单测

**Files:**
- Modify: `app/src/main/java/tiiehenry/android/app/snapshot/group/GroupMembershipResolver.kt`
- Create: `app/src/test/java/tiiehenry/android/app/snapshot/group/GroupMembershipResolverTest.kt`

**Interfaces:**
- Consumes: `containsPackage`, `AppGroupMembership`, `SnapGroup.apps`, `ArchivedApp(packageDir)`
- Produces:
  - `data class JoinTargetCard(val group: SnapGroup, val setId: String?, val userId: Int)`
  - `data class AppsPopupGroupRow(val group: SnapGroup, val exclusive: Boolean)`
  - `fun membershipRows(membership: AppGroupMembership): List<AppsPopupGroupRow>`
  - `fun independentJoinTargets(cards: List<JoinTargetCard>, packageName: String, userId: Int): List<SnapGroup>`
- `userId` 放在 `JoinTargetCard` 上，测试不读 `SnapGroup.userId`（避免 MMKV）

- [ ] **Step 1: Write the failing test**

```kotlin
package tiiehenry.android.app.snapshot.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupMembershipResolverTest {

    private fun groupWith(id: String, vararg pkgs: String): SnapGroup {
        val group = SnapGroup(id)
        pkgs.forEach { pkg ->
            group.apps.add(ArchivedApp(group, "/data/$pkg", "/icons/$pkg.png"))
        }
        return group
    }

    @Test
    fun independentJoinTargets_dropsSetMembersWrongUserAndAlreadyMember() {
        val indie = groupWith("indie")
        val inSet = groupWith("inset")
        val otherUser = groupWith("other")
        val already = groupWith("already", "com.foo")
        val cards = listOf(
            JoinTargetCard(indie, setId = null, userId = 0),
            JoinTargetCard(inSet, setId = "set1", userId = 0),
            JoinTargetCard(otherUser, setId = null, userId = 10),
            JoinTargetCard(already, setId = null, userId = 0),
        )
        val result = GroupMembershipResolver.independentJoinTargets(cards, "com.foo", 0)
        assertEquals(listOf(indie), result)
    }

    @Test
    fun independentJoinTargets_preservesArchiveOrder() {
        val a = groupWith("a")
        val b = groupWith("b")
        val cards = listOf(
            JoinTargetCard(a, null, 0),
            JoinTargetCard(b, null, 0),
        )
        assertEquals(listOf(a, b), GroupMembershipResolver.independentJoinTargets(cards, "com.x", 0))
    }

    @Test
    fun membershipRows_exclusiveThenShared() {
        val ex = groupWith("ex")
        val sh = groupWith("sh")
        val rows = GroupMembershipResolver.membershipRows(
            AppGroupMembership("com.foo", 0, listOf(ex), listOf(sh))
        )
        assertEquals(2, rows.size)
        assertTrue(rows[0].exclusive && rows[0].group === ex)
        assertTrue(!rows[1].exclusive && rows[1].group === sh)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests tiiehenry.android.app.snapshot.group.GroupMembershipResolverTest`

Expected: FAIL — `JoinTargetCard` / methods unresolved

- [ ] **Step 3: Write minimal implementation**

Append to `GroupMembershipResolver.kt` (same file, after existing types or inside the object as needed):

```kotlin
data class JoinTargetCard(
    val group: SnapGroup,
    val setId: String?,
    val userId: Int,
)

data class AppsPopupGroupRow(
    val group: SnapGroup,
    val exclusive: Boolean,
)
```

Inside `object GroupMembershipResolver`:

```kotlin
fun membershipRows(membership: AppGroupMembership): List<AppsPopupGroupRow> =
    membership.exclusiveGroups.map { AppsPopupGroupRow(it, exclusive = true) } +
        membership.sharedGroups.map { AppsPopupGroupRow(it, exclusive = false) }

fun independentJoinTargets(
    cards: List<JoinTargetCard>,
    packageName: String,
    userId: Int,
): List<SnapGroup> =
    cards.filter { it.setId == null }
        .filter { it.userId == userId }
        .filter { !containsPackage(it.group, packageName) }
        .map { it.group }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests tiiehenry.android.app.snapshot.group.GroupMembershipResolverTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/tiiehenry/android/app/snapshot/group/GroupMembershipResolver.kt \
  app/src/test/java/tiiehenry/android/app/snapshot/group/GroupMembershipResolverTest.kt
git commit -m "$(cat <<'EOF'
feat(apps): add independent-group join target resolver

EOF
)"
```

---

### Task 2: 抽出 AppDetailsLauncher

**Files:**
- Create: `app/src/main/java/tiiehenry/android/app/snapshot/utils/AppDetailsLauncher.kt`
- Modify: `app/src/main/java/tiiehenry/android/app/snapshot/main/launch/popup/ArchiveItemPopupMenu.kt` (`openAppSettings` 一段)

**Interfaces:**
- Consumes: `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`, `R.string.archive_cannot_open_app_details`
- Produces: `object AppDetailsLauncher { fun open(context: Context, packageName: String) }`

- [ ] **Step 1: Add launcher**

```kotlin
package tiiehenry.android.app.snapshot.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import tiiehenry.android.app.snapshot.R

object AppDetailsLauncher {
    fun open(context: Context, packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", packageName, null)
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
            } catch (ex: Exception) {
                Toast.makeText(context, R.string.archive_cannot_open_app_details, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }
}
```

- [ ] **Step 2: Replace ArchiveItemPopupMenu.openAppSettings body**

`btnInfo` click 与 `openAppSettings` 改为：

```kotlin
AppDetailsLauncher.open(context, item.appInfo.packageName)
```

删除 private `openAppSettings`。import `tiiehenry.android.app.snapshot.utils.AppDetailsLauncher`。

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`

Expected: SUCCESS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/tiiehenry/android/app/snapshot/utils/AppDetailsLauncher.kt \
  app/src/main/java/tiiehenry/android/app/snapshot/main/launch/popup/ArchiveItemPopupMenu.kt
git commit -m "$(cat <<'EOF'
refactor: extract AppDetailsLauncher for system app settings

EOF
)"
```

---

### Task 3: 抽出 AddAppsResultUi（含 moveApps）

**Files:**
- Create: `app/src/main/java/tiiehenry/android/app/snapshot/group/AddAppsResultUi.kt`
- Modify: `app/src/main/java/tiiehenry/android/app/snapshot/main/launch/GroupActionsController.kt` (`handleAddAppsResult` / `moveApps`)

**Interfaces:**
- Consumes: `AddAppsResult`, `MoveAppResult`, `SnapshotViewModel.resolveGroup` / `moveAppBetweenGroups`, 现有 `group_move_*` / `group_membership_corrupt` / `batch_operation_in_progress`；Task 4 之后的 `apps_popup_already_here` / `apps_popup_add_failed` 先用字面资源——若本任务先于 Task 4，AlreadyHere / Error **本任务先静默**（与现网 Controller 一致），Task 4 补 string 后再在本文件加上 Toast（见 Task 4 Step 3）
- Produces:
  - `object AddAppsResultUi`
  - `fun handle(context: Context, snapshotViewModel: SnapshotViewModel, targetGroupId: String, result: AddAppsResult, onMembershipChanged: () -> Unit)`
  - `fun moveApps(...)` 为 private

- [ ] **Step 1: Create AddAppsResultUi.kt**

把 `GroupActionsController` 219–332 整段搬过来。`onRefresh(target)` 换成 `onMembershipChanged()`。`binding.root.context` 换成参数 `context`。

```kotlin
package tiiehenry.android.app.snapshot.group

import android.app.AlertDialog
import android.content.Context
import android.widget.Toast
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SnapshotViewModel

object AddAppsResultUi {
    fun handle(
        context: Context,
        snapshotViewModel: SnapshotViewModel,
        targetGroupId: String,
        result: AddAppsResult,
        onMembershipChanged: () -> Unit,
    ) {
        val conflicts = result.conflicts
        if (conflicts.isEmpty()) {
            val busy = result.items.values.any { it is AddAppItemResult.Busy }
            val corrupt = result.items.values.any { it is AddAppItemResult.CorruptMultiOwner }
            val error = result.items.values.filterIsInstance<AddAppItemResult.Error>().firstOrNull()
            val alreadyAll = result.items.isNotEmpty() &&
                result.items.values.all { it is AddAppItemResult.AlreadyHere }
            when {
                corrupt -> Toast.makeText(
                    context, R.string.group_membership_corrupt, Toast.LENGTH_LONG
                ).show()
                busy -> Toast.makeText(
                    context, R.string.batch_operation_in_progress, Toast.LENGTH_SHORT
                ).show()
                error != null -> Toast.makeText(
                    context,
                    context.getString(R.string.apps_popup_add_failed, error.message),
                    Toast.LENGTH_LONG
                ).show()
                alreadyAll -> Toast.makeText(
                    context, R.string.apps_popup_already_here, Toast.LENGTH_SHORT
                ).show()
            }
            return
        }
        val target = snapshotViewModel.resolveGroup(targetGroupId) ?: return
        if (conflicts.size == 1) {
            val (pkg, ownerId) = conflicts.entries.first()
            val ownerName = snapshotViewModel.resolveGroup(ownerId)?.name ?: ownerId
            AlertDialog.Builder(context)
                .setTitle(R.string.group_move_conflict_title)
                .setMessage(
                    context.getString(
                        R.string.group_move_conflict_message,
                        pkg,
                        ownerName,
                        target.name
                    )
                )
                .setPositiveButton(R.string.group_move_action) { _, _ ->
                    moveApps(context, snapshotViewModel, mapOf(pkg to ownerId), targetGroupId, onMembershipChanged)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            val lines = conflicts.entries.joinToString("\n") { (pkg, ownerId) ->
                val ownerName = snapshotViewModel.resolveGroup(ownerId)?.name ?: ownerId
                "$pkg → $ownerName"
            }
            AlertDialog.Builder(context)
                .setTitle(R.string.group_move_conflict_title)
                .setMessage(
                    context.getString(R.string.group_move_conflict_multi_message, lines)
                )
                .setPositiveButton(R.string.group_move_all_action) { _, _ ->
                    moveApps(context, snapshotViewModel, conflicts, targetGroupId, onMembershipChanged)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun moveApps(
        context: Context,
        snapshotViewModel: SnapshotViewModel,
        conflicts: Map<String, String>,
        targetGroupId: String,
        onMembershipChanged: () -> Unit,
    ) {
        val entries = conflicts.entries.toList()
        fun moveNext(index: Int) {
            if (index >= entries.size) {
                onMembershipChanged()
                return
            }
            val (pkg, fromId) = entries[index]
            snapshotViewModel.moveAppBetweenGroups(fromId, targetGroupId, pkg) { result ->
                when (result) {
                    is MoveAppResult.Moved, is MoveAppResult.AlreadyAtTarget -> moveNext(index + 1)
                    is MoveAppResult.Busy -> Toast.makeText(
                        context, R.string.batch_operation_in_progress, Toast.LENGTH_SHORT
                    ).show()
                    is MoveAppResult.Locked -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.group_move_failed_locked, pkg),
                            Toast.LENGTH_LONG
                        ).show()
                        moveNext(index + 1)
                    }
                    is MoveAppResult.TargetNonEmpty -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.group_move_failed_target_nonempty, pkg),
                            Toast.LENGTH_LONG
                        ).show()
                        moveNext(index + 1)
                    }
                    is MoveAppResult.CorruptMultiOwner -> {
                        Toast.makeText(context, R.string.group_membership_corrupt, Toast.LENGTH_LONG).show()
                        moveNext(index + 1)
                    }
                    is MoveAppResult.Error -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.group_move_failed_generic, pkg, result.message),
                            Toast.LENGTH_LONG
                        ).show()
                        moveNext(index + 1)
                    }
                }
            }
        }
        moveNext(0)
    }
}
```

若 Task 3 先于 Task 4 提交：先不要引用 `apps_popup_already_here` / `apps_popup_add_failed`，`error` / `alreadyAll` 分支省略；Task 4 Step 3 再加回。

- [ ] **Step 2: Thin GroupActionsController**

删除 `handleAddAppsResult` / `moveApps`。`addAppsToGroup` 回调改为：

```kotlin
snapshotViewModel.addAppsToGroup(targetGroupId, appInfos) { result ->
    onRefresh(resolveGroup(group))
    AddAppsResultUi.handle(
        context = binding.root.context,
        snapshotViewModel = snapshotViewModel,
        targetGroupId = targetGroupId,
        result = result,
        onMembershipChanged = { onRefresh(resolveGroup(group)) },
    )
}
```

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`

Expected: SUCCESS（若已引用未建 string 则 FAIL — 先做 Task 4 Step 1 或按上面省略两分支）

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/tiiehenry/android/app/snapshot/group/AddAppsResultUi.kt \
  app/src/main/java/tiiehenry/android/app/snapshot/main/launch/GroupActionsController.kt
git commit -m "$(cat <<'EOF'
refactor: extract AddAppsResultUi for add/move conflict dialogs

EOF
)"
```

---

### Task 4: i18n + popup 布局

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Create: `app/src/main/res/layout/layout_apps_popup_menu.xml`
- Create: `app/src/main/res/layout/item_apps_popup_group.xml`
- Modify: `AddAppsResultUi.kt` — 若 Task 3 省略了 AlreadyHere/Error Toast，此处补上

**Interfaces:**
- Produces string ids listed below; layouts `LayoutAppsPopupMenuBinding` / `ItemAppsPopupGroupBinding`

- [ ] **Step 1: Add strings（三套 locale 同步）**

`values` / `values-zh-rCN`：

```xml
<string name="apps_popup_add">加入</string>
<string name="apps_popup_add_empty">没有可加入的独立组</string>
<string name="apps_popup_pick_group_title">选择独立组</string>
<string name="apps_popup_already_here">已在该组中</string>
<string name="apps_popup_add_failed">加入失败：%1$s</string>
<string name="apps_popup_group_gone">分组已不存在</string>
<string name="apps_popup_uninstall">卸载</string>
<string name="apps_popup_uninstall_title">卸载 %1$s？</string>
<string name="apps_popup_uninstall_message">将卸载该用户下的应用，分组中的存档不会删除。</string>
<string name="apps_popup_uninstall_failed">卸载失败</string>
```

`values-en`：

```xml
<string name="apps_popup_add">Add</string>
<string name="apps_popup_add_empty">No independent groups to join</string>
<string name="apps_popup_pick_group_title">Choose an independent group</string>
<string name="apps_popup_already_here">Already in this group</string>
<string name="apps_popup_add_failed">Could not add: %1$s</string>
<string name="apps_popup_group_gone">That group no longer exists</string>
<string name="apps_popup_uninstall">Uninstall</string>
<string name="apps_popup_uninstall_title">Uninstall %1$s?</string>
<string name="apps_popup_uninstall_message">This uninstalls the app for this user. Archives in groups are kept.</string>
<string name="apps_popup_uninstall_failed">Uninstall failed</string>
```

- [ ] **Step 2: Add layouts**

`layout_apps_popup_menu.xml` — 复制 `layout_popup_menu.xml` 结构：同一 `popup_menu_background`、`popup_btn_size`、elevation。四键 id：`btn_add`、`btn_info`、`btn_settings`、`btn_uninstall`。图标：`folder_add`、`information_slab_circle_outline`、`cog_transfer`、`delete_outline`。contentDescription：`apps_popup_add`、`desc_info`、`settings`、`apps_popup_uninstall`。`RecyclerView` id `group_list`，`android:maxHeight="200dp"`。

`item_apps_popup_group.xml`：

```xml
<TextView xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/group_label"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="?attr/selectableItemBackground"
    android:padding="8dp"
    android:textColor="?attr/colorOnSurface"
    android:textSize="13sp" />
```

- [ ] **Step 3: Wire AlreadyHere / Error toasts in AddAppsResultUi** if omitted in Task 3

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml \
  app/src/main/res/values-zh-rCN/strings.xml \
  app/src/main/res/values-en/strings.xml \
  app/src/main/res/layout/layout_apps_popup_menu.xml \
  app/src/main/res/layout/item_apps_popup_group.xml \
  app/src/main/java/tiiehenry/android/app/snapshot/group/AddAppsResultUi.kt
git commit -m "$(cat <<'EOF'
feat(apps): add popup strings and layouts

EOF
)"
```

---

### Task 5: AppsItemPopupMenu + 组行 Adapter

**Files:**
- Create: `app/src/main/java/tiiehenry/android/app/snapshot/main/apps/AppsPopupGroupAdapter.kt`
- Create: `app/src/main/java/tiiehenry/android/app/snapshot/main/apps/AppsItemPopupMenu.kt`

**Interfaces:**
- Consumes: Task 1 rows/targets, Task 2 launcher, Task 3 AddAppsResultUi, Task 4 bindings
- Produces: `class AppsItemPopupMenu(context, fragmentManager, snapshotViewModel, onNavigateToGroup, onUninstall)` with `fun show(anchor: View, appInfo: AppInfo, membership: AppGroupMembership)` and `fun dismiss()`

- [ ] **Step 1: Adapter**

```kotlin
package tiiehenry.android.app.snapshot.main.apps

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.databinding.ItemAppsPopupGroupBinding
import tiiehenry.android.app.snapshot.group.AppsPopupGroupRow

class AppsPopupGroupAdapter(
    private val onClick: (AppsPopupGroupRow) -> Unit,
) : RecyclerView.Adapter<AppsPopupGroupAdapter.Holder>() {

    private var rows: List<AppsPopupGroupRow> = emptyList()

    fun submit(list: List<AppsPopupGroupRow>) {
        rows = list
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemAppsPopupGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        val res = if (row.exclusive) R.string.app_membership_exclusive_item
        else R.string.app_membership_shared_item
        holder.binding.groupLabel.text = holder.itemView.context.getString(res, row.group.name)
        holder.itemView.setOnClickListener { onClick(row) }
    }

    class Holder(val binding: ItemAppsPopupGroupBinding) : RecyclerView.ViewHolder(binding.root)
}
```

- [ ] **Step 2: Popup**

`AppsItemPopupMenu`：
- `PopupWindow`：`WRAP_CONTENT`，透明背景，`isOutsideTouchable` / `isFocusable` true，elevation `16f * density`，`showAsDropDown(anchor)`
- 持有 `popupWindow` 供 `dismiss()`
- 列表：`LinearLayoutManager` + `AppsPopupGroupAdapter` + `GroupMembershipResolver.membershipRows(membership)`
- 点组行：`dismiss()`；`snapshotViewModel.resolveGroup(row.group.id) == null` → Toast `apps_popup_group_gone`；否则 `onNavigateToGroup(row.group.id, appInfo.packageName)`
- `btn_settings`：dismiss → `AppConfigFragment.newInstance(packageName, userId).show(fragmentManager, tag)`
- `btn_info`：dismiss → `AppDetailsLauncher.open`
- `btn_add`：从 `snapshotViewModel.archiveList.value` 取 `GroupCard`，映射 `JoinTargetCard(group, setId, group.userId)`，再 `independentJoinTargets(..., appInfo.packageName, appInfo.userId)`。空 → Toast `apps_popup_add_empty`。否则 `AlertDialog.setTitle(apps_popup_pick_group_title).setItems(names)`；选中后 **先 `dismiss()`** 再 `addAppsToGroup(targetId, listOf(appInfo))` → `AddAppsResultUi.handle(..., onMembershipChanged = {})`（筛选刷新靠 Task 7 的 `groupList` 观察）
- `btn_uninstall`：`packageName == context.packageName` 则 `isEnabled = false`。否则确认框后 `onUninstall(appInfo)`（Task 8 才做真卸载；本任务 Fragment 可先 Toast 占位，但更好是接口先留空实现到 Task 8）
- 禁止在 popup 里 mkdir / 写 group.json

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`

Expected: SUCCESS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/tiiehenry/android/app/snapshot/main/apps/AppsPopupGroupAdapter.kt \
  app/src/main/java/tiiehenry/android/app/snapshot/main/apps/AppsItemPopupMenu.kt
git commit -m "$(cat <<'EOF'
feat(apps): add membership popup shell and group rows

EOF
)"
```

---

### Task 6: 接到应用 Tab，删除旧 dialog

**Files:**
- Modify: `app/src/main/java/tiiehenry/android/app/snapshot/main/apps/AppsAdapter.kt`
- Modify: `app/src/main/java/tiiehenry/android/app/snapshot/main/apps/AppsFragment.kt`
- Delete: `app/src/main/java/tiiehenry/android/app/snapshot/main/apps/AppMembershipDialog.kt`

**Interfaces:**
- Consumes: `AppsItemPopupMenu.show(anchor, appInfo, membership)`
- Produces: `onItemLongClick: (View, AppInfo, AppGroupMembership) -> Unit`

- [ ] **Step 1: Change AppsAdapter signature**

`onItemLongClick` 改为 `(View, AppInfo, AppGroupMembership) -> Unit`。`ViewHolder` 同改。长按：

```kotlin
binding.root.setOnLongClickListener {
    onItemLongClick(binding.root, appInfo, membership)
    true
}
```

- [ ] **Step 2: AppsFragment**

字段：`private var appsPopup: AppsItemPopupMenu? = null`

`setupRecyclerViewAdapter`：

```kotlin
appsPopup = AppsItemPopupMenu(
    context = requireContext(),
    fragmentManager = parentFragmentManager,
    snapshotViewModel = snapshotViewModel,
    onNavigateToGroup = { groupId, packageName ->
        snapshotViewModel.requestNavigateToGroup(groupId, packageName)
        (requireActivity() as MainActivity).selectBottomNavTab(R.id.launcherFragment)
    },
    onUninstall = { appInfo ->
        snapshotViewModel.uninstallApp(appInfo.packageName, appInfo.userId) { result ->
            // Task 8 落地前若方法不存在，先留 TODO 编译失败——按顺序先做 Task 8 的 VM 方法或本任务暂不接卸载按钮
        }
    },
)
```

**顺序：** Task 8 的 `uninstallApp` 若尚未存在，本任务 `onUninstall` 先空实现 `{ }`，Task 8 Step 3 再接线。不要在 Fragment 里直接 `appManager.uninstallApk`。

长按：`appsPopup?.show(anchor, appInfo, membership)`  
覆盖 `onDestroyView`：`appsPopup?.dismiss(); appsPopup = null`（若基类已有，先 `dismiss` 再 `super`）

删除 `AppMembershipDialog` 引用与文件。确认无其它引用：`rg AppMembershipDialog`。

- [ ] **Step 3: Compile + unit tests**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests tiiehenry.android.app.snapshot.group.GroupMembershipResolverTest`

Expected: SUCCESS / PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/tiiehenry/android/app/snapshot/main/apps/AppsAdapter.kt \
  app/src/main/java/tiiehenry/android/app/snapshot/main/apps/AppsFragment.kt
git rm app/src/main/java/tiiehenry/android/app/snapshot/main/apps/AppMembershipDialog.kt
git commit -m "$(cat <<'EOF'
feat(apps): replace membership dialog with item popup

EOF
)"
```

---

### Task 7: groupList 变化重跑未分组筛选

**Files:**
- Modify: `app/src/main/java/tiiehenry/android/app/snapshot/main/apps/AppsViewModel.kt`
- Modify: `app/src/main/java/tiiehenry/android/app/snapshot/main/apps/AppsListComponent.kt`

**Interfaces:**
- Consumes: 已有 private `applyFilter()`
- Produces: `fun refreshMembershipFilter() { applyFilter() }`

- [ ] **Step 1: Public wrapper on AppsViewModel**

在 `setMembershipFilter` 旁：

```kotlin
fun refreshMembershipFilter() {
    applyFilter()
}
```

不要把 `applyFilter` 改成 public。

- [ ] **Step 2: Observe groupList in AppsListComponent.onViewCreated**

`groupsProvider` 赋值之后：

```kotlin
snapshotViewModel.groupList.observe(viewLifecycleOwner) {
    viewModel.refreshMembershipFilter()
}
```

`AppsFragment` 现有 `refreshMembership()` 观察保留（改副标题）。不要在 ViewModel 里写盘。

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`

Expected: SUCCESS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/tiiehenry/android/app/snapshot/main/apps/AppsViewModel.kt \
  app/src/main/java/tiiehenry/android/app/snapshot/main/apps/AppsListComponent.kt
git commit -m "$(cat <<'EOF'
fix(apps): reapply membership filter when groups change

EOF
)"
```

---

### Task 8: 卸载（IO + Guard + loadApps）

**Files:**
- Create: `app/src/main/java/tiiehenry/android/app/snapshot/group/UninstallAppResult.kt`
- Modify: `app/src/main/java/tiiehenry/android/app/snapshot/repository/AppDataRepository.kt`
- Modify: `app/src/main/java/tiiehenry/android/app/snapshot/SnapshotViewModel.kt`
- Modify: `app/src/main/java/tiiehenry/android/app/snapshot/main/apps/AppsFragment.kt`（接 `onUninstall`）

**Interfaces:**
- Consumes: `IPackageManager.uninstallApk`, `packageOpGuard`, 已有 `scheduleLoadApps` / `loadApps()`
- Produces:
  - `sealed class UninstallAppResult { data object Success; data object Busy; data object Failed }`
  - `AppDataRepository.uninstallInstalledApp(fileSystem, appManager, packageName, userId, onComplete: (UninstallAppResult) -> Unit)`
  - `SnapshotViewModel.uninstallApp(packageName, userId, onComplete: (UninstallAppResult) -> Unit)`

- [ ] **Step 1: Result type**

```kotlin
package tiiehenry.android.app.snapshot.group

sealed class UninstallAppResult {
    data object Success : UninstallAppResult()
    data object Busy : UninstallAppResult()
    data object Failed : UninstallAppResult()
}
```

- [ ] **Step 2: Repository method（紧挨 `scheduleLoadApps`）**

```kotlin
fun uninstallInstalledApp(
    fileSystem: IFileSystem,
    appManager: IAppManager,
    packageName: String,
    userId: Int,
    onComplete: (UninstallAppResult) -> Unit,
) {
    scope.launch {
        if (packageOpGuard.isGlobalBatchRunning() || packageOpGuard.isBusy()) {
            withContext(Dispatchers.Main) { onComplete(UninstallAppResult.Busy) }
            return@launch
        }
        val ok = appManager.uninstallApk(packageName, userId)
        if (ok) {
            isAppsLoading.postValue(true)
            loadApps(fileSystem, appManager)
            withContext(Dispatchers.Main) { onComplete(UninstallAppResult.Success) }
        } else {
            withContext(Dispatchers.Main) { onComplete(UninstallAppResult.Failed) }
        }
    }
}
```

禁止调用 `loadData`。不要用 `viewModelScope`。

- [ ] **Step 3: VM facade + Fragment**

```kotlin
fun uninstallApp(
    packageName: String,
    userId: Int,
    onComplete: (UninstallAppResult) -> Unit,
) {
    val (_, fileSystem, appManager) = appDeps()
    repository.uninstallInstalledApp(fileSystem, appManager, packageName, userId, onComplete)
}
```

Fragment `onUninstall`：

```kotlin
snapshotViewModel.uninstallApp(appInfo.packageName, appInfo.userId) { result ->
    val ctx = context ?: return@uninstallApp
    when (result) {
        UninstallAppResult.Success -> Unit
        UninstallAppResult.Busy -> Toast.makeText(ctx, R.string.batch_operation_in_progress, Toast.LENGTH_SHORT).show()
        UninstallAppResult.Failed -> Toast.makeText(ctx, R.string.apps_popup_uninstall_failed, Toast.LENGTH_SHORT).show()
    }
}
```

Popup 确认框仍在 `AppsItemPopupMenu`；确认后才调 `onUninstall`。成功路径靠 `loadApps` → `appsList` → 过滤列表，禁止 Adapter 本地 remove。

- [ ] **Step 4: Compile**

Run: `./gradlew :app:compileDebugKotlin`

Expected: SUCCESS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/tiiehenry/android/app/snapshot/group/UninstallAppResult.kt \
  app/src/main/java/tiiehenry/android/app/snapshot/repository/AppDataRepository.kt \
  app/src/main/java/tiiehenry/android/app/snapshot/SnapshotViewModel.kt \
  app/src/main/java/tiiehenry/android/app/snapshot/main/apps/AppsFragment.kt \
  app/src/main/java/tiiehenry/android/app/snapshot/main/apps/AppsItemPopupMenu.kt
git commit -m "$(cat <<'EOF'
feat(apps): uninstall from item popup on repository scope

EOF
)"
```

---

### Task 9: 文档勘误与索引

**Files:**
- Modify: `docs/systems/snapshot/APPS_TAB_ITEM_POPUP.md` — D16 / 文件表：`scheduleLoadApps` 与 VM `loadApps()` **已存在**，本功能只加 `uninstallInstalledApp`
- Modify: `docs/systems/snapshot/APPS_TAB_ITEM_POPUP.md` 状态保持 draft 直到真机验收；Phase 1 清单勾实施进度
- Modify: `dev/plans/overview.md` — 加一行本计划
- Modify: `docs/modules/app/INDEX.md` — `AppMembershipDialog` 改为 `AppsItemPopupMenu`

- [ ] **Step 1: Patch spec D16**

文件表两行改为：

| `AppDataRepository` | 改 | 加 `uninstallInstalledApp`；`scheduleLoadApps` 已存在，不要再加 |
| `SnapshotViewModel` | 改 | 加 `uninstallApp` 门面；`loadApps()` 已存在 |

D16 改为：卸载在 repository `scope` + `packageOpGuard`；成功只 `loadApps`。

- [ ] **Step 2: Overview**

在阶段表增加：

| 应用 Tab Item Popup | planning | [2026-08-23-apps-tab-item-popup](2026-08-23-apps-tab-item-popup.md) | 长按 popup：归属组 + 加入独立组 / 详情 / 卸载 |

`updated: 2026-08-23`

- [ ] **Step 3: Commit**

```bash
git add docs/systems/snapshot/APPS_TAB_ITEM_POPUP.md \
  docs/modules/app/INDEX.md \
  dev/plans/overview.md \
  dev/plans/2026-08-23-apps-tab-item-popup.md
git commit -m "$(cat <<'EOF'
docs: plan apps-tab item popup and correct loadApps note

EOF
)"
```

---

## Device acceptance (after Task 8)

真机（不在单元测试里）：

1. 未分组应用长按 → 列表空；「加入」选独立独占组 → 摘要更新；未分组筛选下该项消失
2. 已在独占 A，加入独立独占 B → 冲突框 → 移动后 A 无 B 有
3. 点已加入的**折叠集内**组 → 走 `requestNavigateToGroup`，集展开且卡片可见
4. 系统信息 / 配置 / 卸载确认取消与成功；卸自己按钮 disabled
5. 存档 Tab 长按 popup 与组 `+` 冲突对话框不回归

---

## Spec coverage

| 方案要求 | 任务 |
|----------|------|
| popup 壳 + 归属组行 + 跳转 | 5, 6 |
| 上排一个「加入」→ 独立组 Dialog | 1, 5 |
| 系统信息 / 配置 | 2, 5 |
| 卸载确认 + IO + Guard + loadApps | 8 |
| AddAppsResultUi 含 move | 3 |
| dismiss-before-add | 5 |
| requestNavigateToGroup / 组已删 Toast | 5 |
| applyFilter on groupList | 7 |
| i18n / maxHeight | 4 |
| 删除 AppMembershipDialog | 6 |
| 不新增 scheduleLoadApps | 已存在；Task 9 勘误 |

## Type consistency

- `JoinTargetCard` / `AppsPopupGroupRow` / `independentJoinTargets` / `membershipRows` — Task 1 定义，Task 5 使用
- `AddAppsResultUi.handle(..., onMembershipChanged)` — Task 3 定义，Task 5/6 使用
- `UninstallAppResult` / `uninstallApp` — Task 8 定义，Fragment 接线
- `AppsItemPopupMenu.show` / `dismiss` — Task 5 定义，Task 6 使用
- `refreshMembershipFilter` — Task 7 定义

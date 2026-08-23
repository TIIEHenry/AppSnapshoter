# Archive Tab Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development.

**Goal:** Add archive-tab search (set name / group name / app label / package) as a view-layer filter without changing `archiveList` SSOT or persisting collapse.

**Architecture:** Pure `ArchiveSearchFilter` + `LauncherViewModel.displayedArchiveList`. Group collapse stays live `renderBody` with a `displayCollapsed` overlay. Set collapse writes are disabled while a query is active (including sticky header and collapse-all).

**Tech Stack:** Kotlin, JUnit, ViewBinding, LiveData, existing `CollapsibleSearchController`

## Global Constraints

- Do not modify `AppDataRepository.reprojectArchiveListLocked` or `ArchiveListProjector` algorithms.
- Do not write `SnapGroup.isCollapsed` / `SnapGroupSet.isCollapsed` / `collapseAllArchive()` in order to reveal search hits.
- `renderBody` must NOT be globally switched to only `GroupCard.collapsed` — use ViewHolder `displayCollapsed`.
- `membersBySetId` must come from `ArchiveListProjector.deriveMembers` then `orderGroups`. Never treat `groupOrder` as membership.
- Sort full `group.apps` with existing `applySorting` first, then truncate by `visiblePackages`. Never call `applySorting` on a subset.
- `tryConsumeNavigate` only after `searchQuery.isBlank()` and `submitList` of the unfiltered `archiveList`.
- `GroupSetHeaderBinder` and sticky overlay share `collapseEnabled`.
- MenuProvider RESUMED rebuilds: `rebindToggle` only; do not `new` Controller on the same search field.
- User-facing strings in `values/strings.xml`, `values-zh-rCN/strings.xml`, and `values-en/strings.xml`.
- No Compose. Work from the worktree root. TDD for Task 1.
- Do not touch unrelated dirty files on the user's main checkout.

---

### Task 1: ArchiveSearchFilter + visiblePackages

**Files:**
- Create: `app/src/main/java/tiiehenry/android/app/snapshot/repository/ArchiveSearchFilter.kt`
- Create: `app/src/test/java/tiiehenry/android/app/snapshot/repository/ArchiveSearchFilterTest.kt`
- Modify: `app/src/main/java/tiiehenry/android/app/snapshot/main/launch/ArchiveListItem.kt`
- Modify: `app/src/main/java/tiiehenry/android/app/snapshot/main/launch/GroupsAdapter.kt` (DiffUtil only: compare `visiblePackages`)
- Modify: `app/src/main/java/tiiehenry/android/app/snapshot/repository/AppDataRepository.kt` (only if `materializeArchiveList` needs an explicit `visiblePackages = null`)

**Interfaces:**
- Consumes: `ArchiveRoot`, `ArchiveListProjector.orderGroups`, `ArchiveListProjector.GroupSnap`
- Produces: `ArchiveSearchFilter.filter(Input): List<DraftItem>`

**Do this:**

1. TDD: write failing `ArchiveSearchFilterTest` covering spec cases 1–10 (empty miss, independent group name/pkg/label, collapsed set name hit emits ALL members, member-only hit emits one card, expanded set with no hits omitted, contiguous blocks, skip `g:` already in a set, name hit ⇒ `visiblePackages==null`, app-only ⇒ package set, case-insensitive, members missing from `groupOrder` still emitted on set-name hit).
2. Implement `ArchiveSearchFilter` as specified in `docs/systems/snapshot/ARCHIVE_SEARCH.md` section `ArchiveSearchFilter` 输入 + 过滤算法.
3. Add `GroupCard.visiblePackages: Set<String>? = null`.
4. DiffUtil `areContentsTheSame` must treat `visiblePackages` change as a content change.
5. Run `./gradlew :app:testDebugUnitTest --tests tiiehenry.android.app.snapshot.repository.ArchiveSearchFilterTest --tests tiiehenry.android.app.snapshot.repository.ArchiveListProjectorTest`
6. Commit.

Exact types (use these names):

```kotlin
object ArchiveSearchFilter {
    data class SearchableApp(val packageName: String, val label: String)
    data class SearchableGroup(val id: String, val name: String, val path: String, val apps: List<SearchableApp>)
    data class SearchableSet(val id: String, val name: String, val groupOrder: List<String>)
    data class Input(
        val query: String,
        val roots: List<ArchiveRoot>,
        val setsById: Map<String, SearchableSet>,
        val groupsById: Map<String, SearchableGroup>,
        val membersBySetId: Map<String, List<SearchableGroup>>,
    )
    sealed class DraftItem {
        data class SetHeader(val setId: String, val groupCount: Int, val expanded: Boolean) : DraftItem()
        data class GroupCard(
            val groupId: String,
            val setId: String?,
            val collapsed: Boolean,
            val visiblePackages: Set<String>?,
        ) : DraftItem()
        data class EmptySetHint(val setId: String) : DraftItem()
    }
    fun filter(input: Input): List<DraftItem>
}
```

Caller never passes a blank query. Match is `contains(query, ignoreCase = true)` on set name, group name, app label, packageName. Reuse `orderGroups` via `GroupSnap(id, path)`. Do not copy basename rules. Do not import `SnapGroup` / MMKV.

---

### Task 2: displayCollapsed, grid filter, highlight, header gate, rebindToggle

**Files:**
- Modify: `GroupsAdapter.kt`, `GroupActionsController.kt`, `GroupItemAdapter.kt`, `GroupSetHeaderBinder.kt`, `GroupSetStickyHeader.kt`
- Create: `app/src/main/java/tiiehenry/android/app/snapshot/ui/widget/TextHighlight.kt`
- Modify: `app/src/main/java/tiiehenry/android/app/snapshot/main/timeline/TimelineTextHighlight.kt` (typealias / delegate)
- Modify: `app/src/main/java/tiiehenry/android/app/snapshot/ui/widget/CollapsibleSearchController.kt` (add `rebindToggle`)
- Modify: `GroupsAdapter` bind to accept `GroupCard` (collapsed snapshot + visiblePackages + searchQuery)

**Interfaces:**
- Consumes: `GroupCard.visiblePackages` from Task 1
- Produces: `displayCollapsed`; `GroupSetHeaderBinder.bind(..., collapseEnabled: Boolean)`; `CollapsibleSearchController.rebindToggle(ImageView)`; Adapter-level `searchQuery` + highlight payload

**Do this:**

1. ViewHolder `displayCollapsed`: on bind, if adapter `searchQuery` is blank use `group.isCollapsed`, else `false`. `renderBody` uses `displayCollapsed`, empty-group still wins. Title click / `scrollToPackage` write MMKV only when query is blank.
2. `refresh`: `applySorting` on full `group.apps`, then filter by `visiblePackages`, then `submitList`.
3. Extract `TextHighlight` from `TimelineTextHighlight`; timeline delegates.
4. Adapter `searchQuery` + payload for group title, set header, app label (like `TimelineAdapter.updateSearchQuery`).
5. `GroupSetHeaderBinder.bind(..., collapseEnabled: Boolean = true)`; sticky passes the same flag. When false, do not call `setGroupSetCollapsed`.
6. `CollapsibleSearchController.rebindToggle(toggle: ImageView)` — swap click listener to the new view, do not re-attach `doOnTextChanged`. Non-empty query must not force `expand()`.
7. Do not wire the archive menu yet (Task 3).
8. Commit.

---

### Task 3: ViewModel + Fragment + menu + i18n + docs

**Files:**
- Modify: `LauncherViewModel.kt`, `LauncherFragment.kt`, `fragment_launcher.xml`, `menu_launcher.xml`
- Create: `res/layout/action_archive_search.xml`
- Modify: three `strings.xml`
- Modify: ui-shell.md, ARCHIVE_SEARCH.md status, group-set-expand-perf.md note if needed
- Modify: `GroupActionsController` refresh to ask ViewModel to rematerialize when query active

**Interfaces:**
- Consumes: `ArchiveSearchFilter`, `deriveMembers`, `orderGroups`, `displayCollapsed` / `collapseEnabled` / `rebindToggle`
- Produces: `LauncherViewModel.searchQuery`, `displayedArchiveList`, `clearSearch()`, `isSearching`

**Do this:**

1. `LauncherViewModel` builds `displayedArchiveList`: blank query → `archiveList`; else Filter + materialize using live `SnapGroup`/`SnapGroupSet`. Assemble `membersBySetId` with `deriveMembers` + current `GlobalConfig.archiveRoots`.
2. Fragment observes `displayedArchiveList` (not raw `archiveList`). Empty state `archive_search_empty`.
3. `menu_search` leftmost with `actionLayout`. `onCreateMenu` finds `actionView` and `rebindToggle`. One Controller instance.
4. Navigate: if pending and query non-blank, `clearSearch()` and do not consume this beat. `tryConsumeNavigate` only when query blank and committed list is unfiltered `archiveList`.
5. `menu_collapse_all` no-op while query non-blank.
6. Single-card refresh: after `loadApps`, rematerialize displayed list for current query (or recompute that card's `visiblePackages`).
7. Strings: `archive_search_hint`, `archive_search_empty` in all three locales. Reuse `timeline_search_toggle` / `timeline_search_close`.
8. Mark spec implemented; sync ui-shell.
9. Run `./gradlew :app:testDebugUnitTest`
10. Commit.

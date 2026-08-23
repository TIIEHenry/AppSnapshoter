package tiiehenry.android.app.snapshot.main.launch

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.tencent.mmkv.MMKV
import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.app.snapshot.archive.restore.ArchiveRestorer
import tiiehenry.android.app.snapshot.config.GlobalConfig
import tiiehenry.android.app.snapshot.group.ArchiveRoot
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.group.SnapGroupSet
import tiiehenry.android.app.snapshot.repository.ArchiveListProjector
import tiiehenry.android.app.snapshot.repository.ArchiveSearchFilter

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    val searchQuery = MutableLiveData("")

    val isSearching: Boolean
        get() = searchQuery.value.orEmpty().trim().isNotBlank()

    private val _displayedArchiveList = MediatorLiveData<List<ArchiveListItem>>()
    val displayedArchiveList: LiveData<List<ArchiveListItem>> = _displayedArchiveList

    private var archiveListSource: LiveData<List<ArchiveListItem>>? = null
    private var groupListSource: LiveData<List<SnapGroup>>? = null
    private var groupSetListSource: LiveData<List<SnapGroupSet>>? = null
    private var sourcesBound = false

    /**
     * 绑定结构 SSOT。过滤只在本 VM 物化，不进 SnapshotViewModel / Repository。
     */
    fun bindArchiveSources(
        archiveList: LiveData<List<ArchiveListItem>>,
        groupList: LiveData<List<SnapGroup>>,
        groupSetList: LiveData<List<SnapGroupSet>>,
    ) {
        if (sourcesBound) return
        sourcesBound = true
        archiveListSource = archiveList
        groupListSource = groupList
        groupSetListSource = groupSetList
        _displayedArchiveList.addSource(archiveList) { rebuildDisplayed() }
        _displayedArchiveList.addSource(groupList) { rebuildDisplayed() }
        _displayedArchiveList.addSource(groupSetList) { rebuildDisplayed() }
        _displayedArchiveList.addSource(searchQuery) { rebuildDisplayed() }
    }

    fun clearSearch() {
        if (searchQuery.value.orEmpty().isEmpty()) return
        searchQuery.value = ""
    }

    /** 单卡 loadApps / onRefresh 后按当前 query 重物化（apps 原地变、groupList 可能不发射）。 */
    fun rematerializeDisplayed() {
        rebuildDisplayed()
    }

    /**
     * 高级恢复：只恢复选中的数据类型
     */
    fun onAdvancedRestoreClicked(
        context: Context,
        archivedApp: ArchivedApp,
        archiveItem: ArchiveItem,
        selectedTypes: Set<String>,
        updateCurrent: () -> Unit
    ) {
        ArchiveRestorer.restoreAdvanced(
            context,
            archivedApp,
            archiveItem,
            selectedTypes,
            updateCurrent,
            viewModelScope
        )
    }

    fun onGroupItemClicked(
        context: Context,
        groupId: String,
        mmkv: MMKV,
        packageName: String,
        item: ArchivedApp,
        updateCurrent: () -> Unit
    ) {
        ArchiveRestorer.restoreLatest(item, context, updateCurrent, viewModelScope)
    }

    private fun rebuildDisplayed() {
        val archive = archiveListSource?.value.orEmpty()
        val query = searchQuery.value.orEmpty().trim()
        if (query.isEmpty()) {
            _displayedArchiveList.value = archive
            return
        }
        val groups = groupListSource?.value.orEmpty()
        val sets = groupSetListSource?.value.orEmpty()
        val roots = GlobalConfig.archiveRoots
        val groupsById = groups.associateBy { it.id }
        val setsById = sets.associateBy { it.id }
        val membersBySetId = assembleMembersBySetId(groups, sets, roots, groupsById)
        val drafts = ArchiveSearchFilter.filter(
            ArchiveSearchFilter.Input(
                query = query,
                roots = roots,
                setsById = sets.associate { set ->
                    set.id to ArchiveSearchFilter.SearchableSet(
                        id = set.id,
                        name = set.name,
                        groupOrder = set.groupOrder,
                    )
                },
                groupsById = groups.associate { it.id to it.toSearchable() },
                membersBySetId = membersBySetId,
            )
        )
        _displayedArchiveList.value = materializeSearchDrafts(drafts, setsById, groupsById)
    }

    /**
     * 成员真源是 path 派生，不是 [SnapGroupSet.groupOrder]。
     * [ArchiveListProjector.deriveMembers] + [ArchiveListProjector.orderGroups]，roots 读当前 [GlobalConfig.archiveRoots]。
     */
    private fun assembleMembersBySetId(
        groups: List<SnapGroup>,
        sets: List<SnapGroupSet>,
        roots: List<ArchiveRoot>,
        groupsById: Map<String, SnapGroup>,
    ): Map<String, List<ArchiveSearchFilter.SearchableGroup>> {
        val derived = ArchiveListProjector.deriveMembers(
            sets = sets.map { ArchiveListProjector.SetSnap(it.id, it.isCollapsed, it.groupOrder) },
            groups = groups.map { ArchiveListProjector.GroupSnap(it.id, it.path) },
            setPaths = sets.associate { it.id to it.path },
            roots = roots,
        )
        return derived.mapValues { (setId, snaps) ->
            val ordered = ArchiveListProjector.orderGroups(
                sets.find { it.id == setId }?.groupOrder.orEmpty(),
                snaps,
            )
            ordered.mapNotNull { snap -> groupsById[snap.id]?.toSearchable() }
        }
    }

    private fun materializeSearchDrafts(
        drafts: List<ArchiveSearchFilter.DraftItem>,
        setsById: Map<String, SnapGroupSet>,
        groupsById: Map<String, SnapGroup>,
    ): List<ArchiveListItem> {
        return drafts.mapNotNull { item ->
            when (item) {
                is ArchiveSearchFilter.DraftItem.SetHeader -> {
                    val set = setsById[item.setId] ?: return@mapNotNull null
                    ArchiveListItem.SetHeader(
                        set = set,
                        groupCount = item.groupCount,
                        expanded = item.expanded,
                        name = set.name,
                        accentColor = set.accentColor,
                    )
                }
                is ArchiveSearchFilter.DraftItem.GroupCard -> {
                    val group = groupsById[item.groupId] ?: return@mapNotNull null
                    val accent = item.setId?.let { setsById[it]?.accentColor }
                    ArchiveListItem.GroupCard(
                        group = group,
                        setId = item.setId,
                        accentColor = accent,
                        collapsed = item.collapsed,
                        visiblePackages = item.visiblePackages,
                        name = group.name,
                        appsFingerprint = archiveAppsFingerprint(group),
                    )
                }
                is ArchiveSearchFilter.DraftItem.EmptySetHint -> {
                    val set = setsById[item.setId] ?: return@mapNotNull null
                    ArchiveListItem.EmptySetHint(set, set.accentColor)
                }
            }
        }
    }

    private fun SnapGroup.toSearchable(): ArchiveSearchFilter.SearchableGroup {
        val apps = synchronized(apps) {
            this.apps.map { app ->
                ArchiveSearchFilter.SearchableApp(
                    packageName = app.appInfo.packageName,
                    label = app.appInfo.label,
                )
            }
        }
        return ArchiveSearchFilter.SearchableGroup(
            id = id,
            name = name,
            path = path,
            apps = apps,
        )
    }
}

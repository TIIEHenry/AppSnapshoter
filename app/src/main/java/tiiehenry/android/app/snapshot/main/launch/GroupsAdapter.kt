package tiiehenry.android.app.snapshot.main.launch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SnapshotViewModel
import tiiehenry.android.app.snapshot.config.SortConfig
import tiiehenry.android.app.snapshot.databinding.ItemEmptySetHintBinding
import tiiehenry.android.app.snapshot.databinding.ItemGroupBinding
import tiiehenry.android.app.snapshot.databinding.ItemGroupSetBinding
import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.main.launch.addgroup.AddGroupBottomSheet
import tiiehenry.android.app.snapshot.main.launch.groupset.GroupSetHeaderBinder
import tiiehenry.android.app.snapshot.ui.widget.TextHighlight
import java.nio.file.Paths

class GroupsAdapter(
    private val viewModel: LauncherViewModel,
    private val snapshotViewModel: SnapshotViewModel,
    private val fragmentManager: FragmentManager
) : ListAdapter<ArchiveListItem, RecyclerView.ViewHolder>(ArchiveDiffCallback()) {

    var isBatchRunning: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount, BATCH_RUNNING_PAYLOAD)
        }

    var searchQuery: String = ""

    fun updateSearchQuery(query: String) {
        if (searchQuery == query) return
        searchQuery = query
        currentList.forEachIndexed { index, item ->
            if (item is ArchiveListItem.SetHeader || item is ArchiveListItem.GroupCard) {
                notifyItemChanged(index, PAYLOAD_HIGHLIGHT)
            }
        }
    }

    /** 提交过滤列表前退出组内拖拽排序，避免半截 drag 写子集顺序。 */
    fun exitActiveSortModes(recyclerView: RecyclerView) {
        for (i in 0 until recyclerView.childCount) {
            val holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(i))
            if (holder is GroupViewHolder) {
                holder.exitSortModeIfActive()
            }
        }
    }

    companion object {
        private const val BATCH_RUNNING_PAYLOAD = "batch_running"
        private const val PAYLOAD_HIGHLIGHT = "highlight"
        private const val VIEW_TYPE_SET = 1
        private const val VIEW_TYPE_GROUP = 2
        private const val VIEW_TYPE_EMPTY_HINT = 3
        private val appItemViewPool = RecyclerView.RecycledViewPool().apply {
            setMaxRecycledViews(0, 24)
        }
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ArchiveListItem.SetHeader -> VIEW_TYPE_SET
        is ArchiveListItem.GroupCard -> VIEW_TYPE_GROUP
        is ArchiveListItem.EmptySetHint -> VIEW_TYPE_EMPTY_HINT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SET -> {
                val binding = ItemGroupSetBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                SetHeaderViewHolder(binding, snapshotViewModel, fragmentManager)
            }
            VIEW_TYPE_EMPTY_HINT -> {
                val binding = ItemEmptySetHintBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                EmptySetHintViewHolder(binding, fragmentManager)
            }
            else -> {
                val binding = ItemGroupBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                GroupViewHolder(binding, viewModel, snapshotViewModel, fragmentManager)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }
        var handled = false
        if (payloads.contains(BATCH_RUNNING_PAYLOAD) && holder is GroupViewHolder) {
            holder.applyBatchRunningState(isBatchRunning)
            handled = true
        }
        if (payloads.contains(PAYLOAD_HIGHLIGHT)) {
            applyHighlightPayload(holder, getItem(position))
            handled = true
        }
        if (!handled) {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ArchiveListItem.SetHeader -> (holder as SetHeaderViewHolder).bind(this, item)
            is ArchiveListItem.EmptySetHint -> (holder as EmptySetHintViewHolder).bind(item)
            is ArchiveListItem.GroupCard -> (holder as GroupViewHolder).bind(this, item)
        }
    }

    private fun applyHighlightPayload(holder: RecyclerView.ViewHolder, item: ArchiveListItem) {
        when {
            holder is SetHeaderViewHolder && item is ArchiveListItem.SetHeader ->
                holder.bind(this, item)
            holder is GroupViewHolder && item is ArchiveListItem.GroupCard ->
                holder.bindHighlight()
        }
    }

    class EmptySetHintViewHolder(
        private val binding: ItemEmptySetHintBinding,
        private val fragmentManager: FragmentManager,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ArchiveListItem.EmptySetHint) {
            binding.hintMembershipRail.setBackgroundColor(item.accentColor)
            binding.root.setOnClickListener {
                val suggested = Paths.get(item.set.path, "group").toString()
                AddGroupBottomSheet.newInstance(
                    suggestedPath = suggested,
                    suggestedName = "group",
                ).show(fragmentManager, AddGroupBottomSheet.TAG)
            }
        }
    }

    class SetHeaderViewHolder(
        private val binding: ItemGroupSetBinding,
        private val snapshotViewModel: SnapshotViewModel,
        private val fragmentManager: FragmentManager,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(adapter: GroupsAdapter, item: ArchiveListItem.SetHeader) {
            GroupSetHeaderBinder.bind(
                binding,
                item,
                snapshotViewModel,
                fragmentManager,
                collapseEnabled = adapter.searchQuery.isBlank(),
                searchQuery = adapter.searchQuery,
            )
        }
    }

    class GroupViewHolder(
        val binding: ItemGroupBinding,
        private val viewModel: LauncherViewModel,
        private val snapshotViewModel: SnapshotViewModel,
        val fragmentManager: FragmentManager
    ) : RecyclerView.ViewHolder(binding.root) {

        var isSortMode = false
        private var itemTouchHelper: ItemTouchHelper? = null
        private lateinit var actionsController: GroupActionsController
        private lateinit var groupsAdapter: GroupsAdapter
        private var boundGroup: SnapGroup? = null
        private var itemAdapter: GroupItemAdapter? = null
        private var visiblePackages: Set<String>? = null
        private var displayCollapsed: Boolean = false
        private val gridSpan = binding.root.resources.getInteger(R.integer.group_app_grid_span)
        private val gridMaxRows = binding.root.resources.getInteger(R.integer.group_app_grid_max_rows)

        init {
            val itemHeight = binding.root.resources.getDimensionPixelSize(R.dimen.group_app_item_height)
            binding.groupRecyclerView.maxHeightPx = itemHeight * gridMaxRows
            binding.groupRecyclerView.layoutManager = GridLayoutManager(binding.root.context, gridSpan)
            binding.groupRecyclerView.setRecycledViewPool(appItemViewPool)
            binding.emptyLayout.setOnClickListener {
                binding.btnAdd.performClick()
            }
        }

        fun bind(
            groupsAdapter: GroupsAdapter,
            card: ArchiveListItem.GroupCard,
        ) {
            val group = card.group
            val inSet = card.setId != null
            val accentColor = card.accentColor
            val groupChanged = boundGroup != null && boundGroup?.id != group.id
            // Recycled holders keep isSortMode; childCount walk cannot see them.
            // Non-blank query must detach ItemTouchHelper before refresh() submits a subset.
            if (shouldExitSortModeOnBind(groupsAdapter.searchQuery, groupChanged, isSortMode)) {
                itemAdapter?.let { stopDragSortMode(it) }
                isSortMode = false
            }
            this.groupsAdapter = groupsAdapter
            boundGroup = group
            visiblePackages = card.visiblePackages
            displayCollapsed = archiveDisplayCollapsed(groupsAdapter.searchQuery, group.isCollapsed)
            applyGroupTitleHighlight(group)
            if (inSet && accentColor != null) {
                binding.setMembershipRail.visibility = View.VISIBLE
                binding.setMembershipRail.setBackgroundColor(accentColor)
            } else {
                binding.setMembershipRail.visibility = View.GONE
            }

            if (!::actionsController.isInitialized) {
                actionsController = GroupActionsController(
                    binding, viewModel, snapshotViewModel, fragmentManager
                ) { g -> refresh(g, binding.groupRecyclerView) }
            }
            actionsController.setupActions(group, groupsAdapter, this)
            actionsController.setBatchRunning(groupsAdapter.isBatchRunning)

            ensureItemAdapter(groupsAdapter, group)
            if (groupChanged) {
                binding.groupRecyclerView.scrollToPosition(0)
            }
            refresh(group, binding.groupRecyclerView)
            syncChromeVisibility()
        }

        private fun ensureItemAdapter(
            groupsAdapter: GroupsAdapter,
            group: SnapGroup,
        ): GroupItemAdapter {
            val existing = itemAdapter
            if (existing != null) {
                existing.group = group
                existing.setBatchRunning(groupsAdapter.isBatchRunning)
                return existing
            }
            val created = GroupItemAdapter(
                this, groupsAdapter, viewModel, snapshotViewModel, group,
                groupsAdapter.isBatchRunning,
            ) { adapter, item ->
                val currentList = ArrayList(adapter.group.apps)
                val index = currentList.indexOfFirst { it.appInfo.packageName == item.appInfo.packageName }
                if (index != -1) {
                    currentList[index] = item
                    adapter.submitList(currentList)
                }
            }
            itemAdapter = created
            binding.groupRecyclerView.adapter = created
            return created
        }

        private fun syncInnerGridScrolling(appCount: Int) {
            val rv = binding.groupRecyclerView
            val canScroll = appCount > gridSpan * gridMaxRows
            val lp = rv.layoutParams
            val targetHeight = if (canScroll) rv.maxHeightPx else ViewGroup.LayoutParams.WRAP_CONTENT
            if (lp.height != targetHeight) {
                lp.height = targetHeight
                rv.layoutParams = lp
            }
        }

        fun applyBatchRunningState(running: Boolean) {
            if (!::actionsController.isInitialized) return
            actionsController.setBatchRunning(running)
            (binding.groupRecyclerView.adapter as? GroupItemAdapter)?.setBatchRunning(running)
        }

        fun exitSortModeIfActive() {
            if (!isSortMode) return
            val group = boundGroup ?: return
            val adapter = itemAdapter ?: return
            toggleSortMode(group, adapter)
        }

        fun toggleSortMode(group: SnapGroup, adapter: GroupItemAdapter) {
            if (!isSortMode && ::groupsAdapter.isInitialized && groupsAdapter.searchQuery.isNotBlank()) {
                return
            }
            isSortMode = !isSortMode
            boundGroup = group
            if (isSortMode) {
                startDragSortMode(adapter, group)
                binding.groupTitle.text = binding.root.context.getString(R.string.group_sort_mode_title, group.name)
            } else {
                applyGroupTitleHighlight(group)
                stopDragSortMode(adapter)
            }
            syncChromeVisibility()
        }

        private fun startDragSortMode(adapter: GroupItemAdapter, group: SnapGroup) {
            val callback = object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
            ) {
                override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                    val fromPosition = vh.adapterPosition
                    val toPosition = target.adapterPosition
                    val currentList = adapter.currentList.toMutableList()
                    val item = currentList[fromPosition]
                    currentList.removeAt(fromPosition)
                    currentList.add(toPosition, item)
                    adapter.submitList(currentList)
                    saveSortOrderToConfig(currentList, group)
                    return true
                }

                override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
                override fun isLongPressDragEnabled() = false
                override fun isItemViewSwipeEnabled() = false
            }
            itemTouchHelper = ItemTouchHelper(callback)
            itemTouchHelper?.attachToRecyclerView(binding.groupRecyclerView)
            adapter.itemTouchHelper = itemTouchHelper
            adapter.notifyDataSetChanged()
        }

        private fun stopDragSortMode(adapter: GroupItemAdapter) {
            itemTouchHelper?.attachToRecyclerView(null)
            itemTouchHelper = null
            adapter.itemTouchHelper = null
            adapter.notifyDataSetChanged()
        }

        private fun saveSortOrderToConfig(sortedList: List<ArchivedApp>, group: SnapGroup) {
            val sortConfig = group.config.sortConfig
            sortConfig.sortOrder = sortedList.map { it.appInfo.packageName }.toMutableList()
            sortConfig.sortType = SortConfig.SORT_TYPE_CUSTOM
            group.config.save()
        }

        fun refresh(group: SnapGroup, recyclerView: RecyclerView) {
            boundGroup = group
            applyGroupTitleHighlight(group)
            val sortedApps = synchronized(group.apps) {
                applySorting(group.apps, group.config.sortConfig, group)
            }
            val displayed = filterVisibleApps(sortedApps, visiblePackages)
            val adapter = recyclerView.adapter as GroupItemAdapter
            adapter.group = group
            syncInnerGridScrolling(displayed.size)
            adapter.submitList(displayed)
            renderBody(group)
            syncChromeVisibility()
        }

        fun bindHighlight() {
            val group = boundGroup ?: return
            if (::groupsAdapter.isInitialized) {
                displayCollapsed = archiveDisplayCollapsed(groupsAdapter.searchQuery, group.isCollapsed)
            }
            applyGroupTitleHighlight(group)
            itemAdapter?.updateHighlight()
            renderBody(group)
            syncChromeVisibility()
        }

        fun updateCollapseState(isCollapsed: Boolean) {
            displayCollapsed = isCollapsed
            val group = boundGroup ?: return
            renderBody(group)
            syncChromeVisibility()
        }

        /** 展开分组并滚到指定包名（用于应用 Tab / 时间线跳转）。 */
        fun scrollToPackage(packageName: String) {
            val group = boundGroup ?: return
            val persistCollapse = !::groupsAdapter.isInitialized || groupsAdapter.searchQuery.isBlank()
            if (persistCollapse && group.isCollapsed) {
                group.isCollapsed = false
                updateCollapseState(false)
            }
            val adapter = itemAdapter ?: return
            val index = adapter.currentList.indexOfFirst {
                it.appInfo.packageName == packageName
            }
            if (index < 0) return
            binding.groupRecyclerView.post {
                binding.groupRecyclerView.scrollToPosition(index)
            }
        }

        /**
         * 分组 body 三态互斥投影：
         * - 空组 → 仅 empty_layout（忽略 displayCollapsed，始终加号）
         * - 有应用且折叠 → 仅 expand_group
         * - 有应用且展开 → 仅 app_layout
         * 折叠看 [displayCollapsed]，不直接读 live getter / [ArchiveListItem.GroupCard.collapsed]。
         */
        private fun renderBody(group: SnapGroup) {
            binding.progressBar.visibility = View.GONE
            val isEmpty = synchronized(group.apps) { group.apps.isEmpty() }
            when {
                isEmpty -> {
                    binding.expandGroup.visibility = View.GONE
                    binding.emptyLayout.visibility = View.VISIBLE
                    binding.appLayout.visibility = View.GONE
                    binding.groupRecyclerView.visibility = View.GONE
                }
                displayCollapsed -> {
                    binding.expandGroup.visibility = View.VISIBLE
                    binding.emptyLayout.visibility = View.GONE
                    binding.appLayout.visibility = View.GONE
                }
                else -> {
                    binding.expandGroup.visibility = View.GONE
                    binding.emptyLayout.visibility = View.GONE
                    binding.appLayout.visibility = View.VISIBLE
                    binding.groupRecyclerView.visibility = View.VISIBLE
                }
            }
        }

        private fun syncChromeVisibility() {
            if (!::actionsController.isInitialized) return
            val showActions = !isSortMode
            val isEmpty = boundGroup?.apps?.isEmpty() == true
            actionsController.updateButtonVisibility(showActions, isEmpty = isEmpty)
        }

        private fun applySorting(apps: List<ArchivedApp>, sortConfig: SortConfig, group: SnapGroup): List<ArchivedApp> {
            return when (sortConfig.sortType) {
                SortConfig.SORT_TYPE_CUSTOM -> {
                    val sortOrder = sortConfig.sortOrder.toMutableList()
                    val appPackageNames = apps.map { it.appInfo.packageName }
                    sortOrder.removeAll { it !in appPackageNames }
                    val newApps = appPackageNames.filter { it !in sortOrder }
                    sortOrder.addAll(newApps)
                    if (sortOrder != sortConfig.sortOrder) {
                        sortConfig.sortOrder = sortOrder
                        group.config.save()
                    }
                    apps.sortedBy { sortOrder.indexOf(it.appInfo.packageName) }
                }
                SortConfig.SORT_TYPE_NAME_DESC -> apps.sortedByDescending { it.appInfo.label }
                SortConfig.SORT_TYPE_NAME_ASC -> apps.sortedBy { it.appInfo.label }
                SortConfig.SORT_TYPE_INSTALL_TIME_ASC -> apps.sortedBy { it.appInfo.packageInfo?.firstInstallTime ?: 0L }
                SortConfig.SORT_TYPE_INSTALL_TIME_DESC -> apps.sortedByDescending { it.appInfo.packageInfo?.firstInstallTime ?: 0L }
                else -> apps.toList()
            }
        }

        private fun applyGroupTitleHighlight(group: SnapGroup) {
            if (isSortMode) {
                binding.groupTitle.text = binding.root.context.getString(R.string.group_sort_mode_title, group.name)
                return
            }
            val query = if (::groupsAdapter.isInitialized) groupsAdapter.searchQuery.trim() else ""
            binding.groupTitle.text = TextHighlight.highlight(binding.root.context, group.name, query)
        }
    }

    /**
     * DiffUtil：折叠相关字段只比投影快照（[ArchiveListItem.SetHeader.expanded]、
     * [ArchiveListItem.GroupCard.collapsed]），禁止读 [SnapGroup.isCollapsed] /
     * [tiiehenry.android.app.snapshot.group.SnapGroupSet.isCollapsed] live getter。
     * [ArchiveListItem.GroupCard.visiblePackages] / [ArchiveListItem.GroupCard.appsFingerprint]
     * 变化视为内容变化。禁止读 live [SnapGroup.apps]。
     */
    private class ArchiveDiffCallback : DiffUtil.ItemCallback<ArchiveListItem>() {
        override fun areItemsTheSame(oldItem: ArchiveListItem, newItem: ArchiveListItem): Boolean {
            return when {
                oldItem is ArchiveListItem.SetHeader && newItem is ArchiveListItem.SetHeader ->
                    oldItem.set.id == newItem.set.id
                oldItem is ArchiveListItem.GroupCard && newItem is ArchiveListItem.GroupCard ->
                    oldItem.group.id == newItem.group.id
                oldItem is ArchiveListItem.EmptySetHint && newItem is ArchiveListItem.EmptySetHint ->
                    oldItem.set.id == newItem.set.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: ArchiveListItem, newItem: ArchiveListItem): Boolean {
            return when {
                oldItem is ArchiveListItem.SetHeader && newItem is ArchiveListItem.SetHeader ->
                    oldItem.name == newItem.name &&
                        oldItem.groupCount == newItem.groupCount &&
                        oldItem.expanded == newItem.expanded &&
                        oldItem.accentColor == newItem.accentColor
                oldItem is ArchiveListItem.EmptySetHint && newItem is ArchiveListItem.EmptySetHint ->
                    oldItem.set.path == newItem.set.path &&
                        oldItem.accentColor == newItem.accentColor
                oldItem is ArchiveListItem.GroupCard && newItem is ArchiveListItem.GroupCard ->
                    archiveGroupCardContentsTheSame(oldItem, newItem)
                else -> false
            }
        }
    }
}

internal fun archiveDisplayCollapsed(searchQuery: String, groupIsCollapsed: Boolean): Boolean =
    if (searchQuery.isBlank()) groupIsCollapsed else false

/** Recycled holders keep isSortMode; bind must not wait for attached children. */
internal fun shouldExitSortModeOnBind(
    searchQuery: String,
    groupChanged: Boolean,
    isSortMode: Boolean,
): Boolean {
    if (!isSortMode) return false
    return searchQuery.isNotBlank() || groupChanged
}

internal fun filterVisibleApps(
    sorted: List<ArchivedApp>,
    visiblePackages: Set<String>?,
): List<ArchivedApp> =
    visiblePackages?.let { pkgs -> sorted.filter { it.appInfo.packageName in pkgs } } ?: sorted

/**
 * GroupCard 内容比较只吃投影快照（name / collapsed / fingerprint），禁止读 live apps。
 * 同一 [SnapGroup] 原地改 apps 后，新旧卡片必须靠 [ArchiveListItem.GroupCard.appsFingerprint] 才能 Diff 到。
 */
internal fun archiveGroupCardContentsTheSame(
    oldItem: ArchiveListItem.GroupCard,
    newItem: ArchiveListItem.GroupCard,
): Boolean {
    if (oldItem.name != newItem.name) return false
    if (oldItem.collapsed != newItem.collapsed) return false
    if (oldItem.setId != newItem.setId) return false
    if (oldItem.accentColor != newItem.accentColor) return false
    if (oldItem.visiblePackages != newItem.visiblePackages) return false
    return oldItem.appsFingerprint == newItem.appsFingerprint
}


package tiiehenry.android.app.snapshot.main.timeline

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.datepicker.MaterialDatePicker
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SingletonViewModelFactory
import tiiehenry.android.app.snapshot.config.GlobalConfig
import tiiehenry.android.app.snapshot.main.MainActivity
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.SnapshotViewModel
import tiiehenry.android.app.snapshot.databinding.FragmentTimelineBinding
import tiiehenry.android.app.snapshot.ui.widget.CollapsibleSearchController
import tiiehenry.android.app.snapshot.utils.GroupPathPickerHelper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class TimelineFragment : Fragment() {

    private var _binding: FragmentTimelineBinding? = null
    private val binding get() = _binding!!

    private val snapshotViewModel: SnapshotViewModel by activityViewModels {
        SingletonViewModelFactory(SnapshotApp.getViewModel())
    }
    private val timelineViewModel: TimelineViewModel by activityViewModels()

    private lateinit var adapter: TimelineAdapter
    private lateinit var batchOperator: TimelineBatchOperator
    private lateinit var exportDirPicker: GroupPathPickerHelper
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var bottomNavigationContainer: View

    private var pendingExportEntries: List<TimelineEntry> = emptyList()
    private var pendingExportGroups: List<tiiehenry.android.app.snapshot.group.SnapGroup> = emptyList()
    private var pendingExportTimeRange: TimeRange = TimelineRepository.defaultLast7Days()

    private var previousChipId: Int = R.id.chip_7days
    private var searchController: CollapsibleSearchController? = null

    private val customChipFormatter = DateTimeFormatter.ofPattern("M/d", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exportDirPicker = GroupPathPickerHelper(this) { absolutePath, _ ->
            if (absolutePath.isNotEmpty() && absolutePath != "null") {
                val exportDir = "$absolutePath/AppSnapshotExport"
                batchOperator.batchExport(pendingExportEntries, pendingExportGroups, pendingExportTimeRange, exportDir)
            }
        }
        exportDirPicker.register()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTimelineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bottomNavigation = requireActivity().findViewById(R.id.bottom_navigation)
        bottomNavigationContainer = requireActivity().findViewById(R.id.bottom_navigation_container)

        batchOperator = TimelineBatchOperator(requireContext(), viewLifecycleOwner.lifecycleScope, snapshotViewModel, timelineViewModel)

        setupRecyclerView()
        setupChips()
        setupSearch()
        setupMultiSelectToolbar()
        setupActionBar()
        setupEmptyState()
        setupHeatmap()
        observeViewModel()

        timelineViewModel.bindGroupList(snapshotViewModel.groupList)

        val persistedRange = timelineViewModel.timeRange.value
        if (persistedRange?.preset == TimePreset.CUSTOM) {
            updateCustomChipLabel(persistedRange)
        }
    }

    private fun setupRecyclerView() {
        adapter = TimelineAdapter(
            onItemClick = { entry ->
                snapshotViewModel.navigateToGroup.value = entry.key.groupId
                bottomNavigation.selectedItemId = R.id.launcherFragment
            },
            onMultiSelectModeChanged = { _ ->
                timelineViewModel.enterMultiSelectMode()
            },
            onSelectionChanged = { selectedIds ->
                timelineViewModel.selectedIds.value = selectedIds
            }
        )
        binding.timelineRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.timelineRecyclerView.adapter = adapter
        binding.timelineRecyclerView.addItemDecoration(TimelineStickyHeaderDecoration(adapter))
        updateTimelineRecyclerBottomPadding(multiSelect = false)
    }

    private fun updateTimelineRecyclerBottomPadding(multiSelect: Boolean) {
        val bottom = if (multiSelect) {
            resources.getDimensionPixelSize(R.dimen.floating_nav_content_gap)
        } else {
            (requireActivity() as MainActivity).floatingNavContentPaddingBottom()
        }
        binding.timelineRecyclerView.updatePadding(bottom = bottom)
    }

    fun updateBottomContentPadding() {
        if (_binding == null) return
        updateTimelineRecyclerBottomPadding(
            timelineViewModel.isMultiSelectMode.value == true
        )
    }

    private fun setupChips() {
        restorePersistedChip()
        binding.chipGroupTime.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val chipId = checkedIds.first()
            if (chipId == R.id.chip_custom) {
                showCustomDateRangePicker()
                return@setOnCheckedStateChangeListener
            }
            previousChipId = chipId
            resetCustomChipLabel()
            val preset = when (chipId) {
                R.id.chip_today -> TimePreset.TODAY
                R.id.chip_yesterday -> TimePreset.YESTERDAY
                R.id.chip_7days -> TimePreset.LAST_7_DAYS
                R.id.chip_30days -> TimePreset.LAST_30_DAYS
                else -> TimePreset.LAST_7_DAYS
            }
            GlobalConfig.timelinePreset = preset.name
            timelineViewModel.setTimeRange(TimelineRepository.resolveTimeRange(preset))
        }
    }

    private fun restorePersistedChip() {
        val presetName = GlobalConfig.timelinePreset ?: return
        val chipId = when (presetName) {
            TimePreset.TODAY.name -> R.id.chip_today
            TimePreset.YESTERDAY.name -> R.id.chip_yesterday
            TimePreset.LAST_7_DAYS.name -> R.id.chip_7days
            TimePreset.LAST_30_DAYS.name -> R.id.chip_30days
            TimePreset.CUSTOM.name -> R.id.chip_custom
            else -> return
        }
        previousChipId = if (chipId == R.id.chip_custom) R.id.chip_7days else chipId
        binding.chipGroupTime.check(chipId)
    }

    private fun showCustomDateRangePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.timeline_custom)
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            val startMillis = selection.first
            val endMillis = selection.second
            val endTimeExclusive = endMillis + 24 * 60 * 60 * 1000L
            GlobalConfig.timelinePreset = TimePreset.CUSTOM.name
            GlobalConfig.timelineCustomStart = startMillis
            GlobalConfig.timelineCustomEnd = endTimeExclusive
            previousChipId = R.id.chip_custom
            val range = TimeRange(startMillis, endTimeExclusive, TimePreset.CUSTOM)
            updateCustomChipLabel(range)
            timelineViewModel.setTimeRange(range)
        }

        picker.addOnNegativeButtonClickListener {
            revertToPreviousChip()
        }

        picker.addOnCancelListener {
            revertToPreviousChip()
        }

        picker.show(parentFragmentManager, "timeline_custom_date_range")
    }

    private fun revertToPreviousChip() {
        binding.chipGroupTime.check(previousChipId)
        if (previousChipId != R.id.chip_custom) {
            resetCustomChipLabel()
        }
    }

    private fun updateCustomChipLabel(range: TimeRange) {
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(range.startTime).atZone(zone).toLocalDate()
        val end = Instant.ofEpochMilli(range.endTimeExclusive - 1).atZone(zone).toLocalDate()
        val label = if (start == end) {
            start.format(customChipFormatter)
        } else {
            "${start.format(customChipFormatter)}–${end.format(customChipFormatter)}"
        }
        binding.chipCustom.text = label
    }

    private fun resetCustomChipLabel() {
        binding.chipCustom.text = getString(R.string.timeline_custom)
    }

    private fun setupSearch() {
        searchController = CollapsibleSearchController(
            toggle = binding.btnSearchToggle,
            searchField = binding.searchField,
            transitionHost = binding.filterHeader,
            onQueryChanged = { query -> timelineViewModel.searchQuery.value = query },
            hint = getString(R.string.timeline_search_hint),
            initialQuery = timelineViewModel.searchQuery.value.orEmpty()
        )
    }

    private fun setupMultiSelectToolbar() {
        binding.multiSelectToolbar.confirmButton.visibility = View.GONE
        binding.multiSelectToolbar.selectAllButton.setOnClickListener {
            timelineViewModel.selectAll()
        }
        binding.multiSelectToolbar.cancelButton.setOnClickListener {
            timelineViewModel.clearSelection()
        }
    }

    private fun setupActionBar() {
        binding.btnRestore.setOnClickListener {
            val selectedIds = timelineViewModel.selectedIds.value.orEmpty()
            if (selectedIds.isEmpty()) {
                Toast.makeText(requireContext(), R.string.timeline_no_selection, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selectedEntries = timelineViewModel.entries.value.orEmpty()
                .filter { it.key.id in selectedIds }
            val groups = snapshotViewModel.groupList.value.orEmpty()
            val timeRange = timelineViewModel.timeRange.value ?: TimelineRepository.defaultLast7Days()

            val multiSnapshotCount = selectedEntries.count { it.matchingArchiveNames.size > 1 }
            if (multiSnapshotCount > 0) {
                RestoreStrategyDialog.show(
                    requireContext(),
                    selectedEntries.size,
                    multiSnapshotCount
                ) { strategy ->
                    confirmAndRestore(selectedEntries, groups, timeRange, strategy)
                }
            } else {
                confirmAndRestore(selectedEntries, groups, timeRange, RestoreStrategy.NEWEST_FIRST)
            }
        }

        binding.btnDelete.setOnClickListener {
            val selectedIds = timelineViewModel.selectedIds.value.orEmpty()
            if (selectedIds.isEmpty()) {
                Toast.makeText(requireContext(), R.string.timeline_no_selection, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selectedEntries = timelineViewModel.entries.value.orEmpty()
                .filter { it.key.id in selectedIds }
            val groups = snapshotViewModel.groupList.value.orEmpty()
            val timeRange = timelineViewModel.timeRange.value ?: TimelineRepository.defaultLast7Days()

            batchOperator.batchDelete(selectedEntries, groups, timeRange) {
                timelineViewModel.clearSelection()
            }
        }

        binding.btnExport.setOnClickListener {
            val selectedIds = timelineViewModel.selectedIds.value.orEmpty()
            if (selectedIds.isEmpty()) {
                Toast.makeText(requireContext(), R.string.timeline_no_selection, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pendingExportEntries = timelineViewModel.entries.value.orEmpty()
                .filter { it.key.id in selectedIds }
            pendingExportGroups = snapshotViewModel.groupList.value.orEmpty()
            pendingExportTimeRange = timelineViewModel.timeRange.value ?: TimelineRepository.defaultLast7Days()
            exportDirPicker.launch()
        }
    }

    private fun setupEmptyState() {
        binding.btnGoArchive.setOnClickListener {
            bottomNavigation.selectedItemId = R.id.launcherFragment
        }
    }

    private fun setupHeatmap() {
        binding.heatmapView.setOnDayClickListener { day ->
            filterToSingleDay(day)
        }
    }

    private fun filterToSingleDay(day: LocalDate) {
        val zone = ZoneId.systemDefault()
        val startMillis = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val endTimeExclusive = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        GlobalConfig.timelinePreset = TimePreset.CUSTOM.name
        GlobalConfig.timelineCustomStart = startMillis
        GlobalConfig.timelineCustomEnd = endTimeExclusive
        previousChipId = R.id.chip_custom
        val range = TimeRange(startMillis, endTimeExclusive, TimePreset.CUSTOM)
        binding.chipGroupTime.check(R.id.chip_custom)
        updateCustomChipLabel(range)
        timelineViewModel.setTimeRange(range)
    }

    private fun confirmAndRestore(
        entries: List<TimelineEntry>,
        groups: List<tiiehenry.android.app.snapshot.group.SnapGroup>,
        timeRange: TimeRange,
        strategy: RestoreStrategy
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.timeline_restore)
            .setMessage(getString(R.string.timeline_restore_confirm, entries.size))
            .setPositiveButton(R.string.timeline_restore) { _, _ ->
                batchOperator.batchRestore(entries, groups, timeRange, strategy)
                timelineViewModel.clearSelection()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun observeViewModel() {
        timelineViewModel.searchQuery.observe(viewLifecycleOwner) { query ->
            adapter.updateSearchQuery(query)
        }

        timelineViewModel.filteredEntries.observe(viewLifecycleOwner) { entries ->
            adapter.searchQuery = queryFromViewModel()
            val items = TimelineGrouping.groupEntries(entries, requireContext())
            adapter.submitList(items)
            updateHeatmap()
            updateEmptyState(entries)
        }

        timelineViewModel.isQuerying.observe(viewLifecycleOwner) { querying ->
            binding.progressBar.visibility = if (querying) View.VISIBLE else View.GONE
            if (!querying) {
                updateEmptyState(timelineViewModel.filteredEntries.value.orEmpty())
            }
        }

        timelineViewModel.isMultiSelectMode.observe(viewLifecycleOwner) { isMulti ->
            adapter.setMultiSelectMode(isMulti)
            binding.multiSelectToolbar.root.visibility = if (isMulti) View.VISIBLE else View.GONE
            binding.actionBar.visibility = if (isMulti) View.VISIBLE else View.GONE
            binding.filterHeader.visibility = if (isMulti) View.GONE else View.VISIBLE
            bottomNavigationContainer.visibility = if (isMulti) View.GONE else View.VISIBLE
            updateTimelineRecyclerBottomPadding(isMulti)
            if (!isMulti) {
                binding.multiSelectToolbar.selectedCountText.text = getString(R.string.selected_count, 0)
            }
        }

        timelineViewModel.selectedIds.observe(viewLifecycleOwner) { ids ->
            adapter.setSelectedIds(ids)
            binding.multiSelectToolbar.selectedCountText.text = getString(R.string.selected_count, ids.size)
        }

        timelineViewModel.isBatchRunning.observe(viewLifecycleOwner) { running ->
            binding.btnRestore.isEnabled = !running
            binding.btnDelete.isEnabled = !running
            binding.btnExport.isEnabled = !running
        }

        timelineViewModel.timeRange.observe(viewLifecycleOwner) { range ->
            if (range.preset == TimePreset.CUSTOM) {
                updateCustomChipLabel(range)
            }
        }
    }

    private fun updateHeatmap() {
        val entries = timelineViewModel.entries.value.orEmpty()
        val range = timelineViewModel.timeRange.value ?: TimelineRepository.defaultLast7Days()
        val zone = ZoneId.systemDefault()
        val startDate = Instant.ofEpochMilli(range.startTime).atZone(zone).toLocalDate()
        val endDate = Instant.ofEpochMilli(range.endTimeExclusive - 1).atZone(zone).toLocalDate()
        binding.heatmapView.setData(entries, startDate, endDate)
    }

    private fun queryFromViewModel(): String =
        timelineViewModel.searchQuery.value.orEmpty()

    private fun updateEmptyState(entries: List<TimelineEntry>) {
        binding.emptyState.visibility = if (entries.isEmpty() && timelineViewModel.isQuerying.value != true) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    override fun onDestroyView() {
        bottomNavigationContainer.visibility = View.VISIBLE
        updateTimelineRecyclerBottomPadding(multiSelect = false)
        super.onDestroyView()
        _binding = null
    }
}

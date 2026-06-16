package tiiehenry.android.app.snapshot.main.timeline

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.datepicker.MaterialDatePicker
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SingletonViewModelFactory
import tiiehenry.android.app.snapshot.config.GlobalConfig
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.SnapshotViewModel
import tiiehenry.android.app.snapshot.databinding.FragmentTimelineBinding
import tiiehenry.android.app.snapshot.utils.GroupPathPickerHelper

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

    private var pendingExportEntries: List<TimelineEntry> = emptyList()
    private var pendingExportGroups: List<tiiehenry.android.app.snapshot.group.SnapGroup> = emptyList()
    private var pendingExportTimeRange: TimeRange = TimelineRepository.defaultLast7Days()

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

        batchOperator = TimelineBatchOperator(requireContext(), viewLifecycleOwner.lifecycleScope, snapshotViewModel, timelineViewModel)

        setupRecyclerView()
        setupChips()
        setupSearch()
        setupMultiSelectToolbar()
        setupActionBar()
        observeViewModel()

        timelineViewModel.bindGroupList(snapshotViewModel.groupList)
    }

    private fun setupRecyclerView() {
        adapter = TimelineAdapter(
            onItemClick = { entry ->
                snapshotViewModel.navigateToGroup.value = entry.key.groupId
                requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(
                    R.id.bottom_navigation
                ).selectedItemId = R.id.launcherFragment
            },
            onMultiSelectModeChanged = { isMultiSelect ->
                timelineViewModel.enterMultiSelectMode()
            },
            onSelectionChanged = { selectedIds ->
                timelineViewModel.selectedIds.value = selectedIds
            }
        )
        binding.timelineRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.timelineRecyclerView.setHasFixedSize(true)
        binding.timelineRecyclerView.adapter = adapter
    }

    private var previousChipId: Int = R.id.chip_7days

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
            // Convert inclusive end date to exclusive (next day 00:00)
            val endTimeExclusive = endMillis + 24 * 60 * 60 * 1000L
            GlobalConfig.timelinePreset = TimePreset.CUSTOM.name
            GlobalConfig.timelineCustomStart = startMillis
            GlobalConfig.timelineCustomEnd = endTimeExclusive
            previousChipId = R.id.chip_custom
            val range = TimeRange(startMillis, endTimeExclusive, TimePreset.CUSTOM)
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
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : android.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                timelineViewModel.searchQuery.value = newText.orEmpty()
                return true
            }
        })
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
        timelineViewModel.filteredEntries.observe(viewLifecycleOwner) { entries ->
            adapter.submitList(entries)
            updateHeatmap()
            binding.emptyState.visibility = if (entries.isEmpty() && timelineViewModel.isQuerying.value != true) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        timelineViewModel.isQuerying.observe(viewLifecycleOwner) { querying ->
            binding.progressBar.visibility = if (querying) View.VISIBLE else View.GONE
        }

        timelineViewModel.isMultiSelectMode.observe(viewLifecycleOwner) { isMulti ->
            adapter.setMultiSelectMode(isMulti)
            binding.multiSelectToolbar.root.visibility = if (isMulti) View.VISIBLE else View.GONE
            binding.actionBar.visibility = if (isMulti) View.VISIBLE else View.GONE
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
    }

    private fun updateHeatmap() {
        val entries = timelineViewModel.entries.value.orEmpty()
        val range = timelineViewModel.timeRange.value ?: TimelineRepository.defaultLast7Days()
        val zone = java.time.ZoneId.systemDefault()
        val startDate = java.time.Instant.ofEpochMilli(range.startTime).atZone(zone).toLocalDate()
        val endDate = java.time.Instant.ofEpochMilli(range.endTimeExclusive - 1).atZone(zone).toLocalDate()
        binding.heatmapView.setData(entries, startDate, endDate)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

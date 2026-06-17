package tiiehenry.android.app.snapshot.main.timeline

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tiiehenry.android.app.snapshot.config.GlobalConfig
import tiiehenry.android.app.snapshot.group.SnapGroup

class TimelineViewModel : ViewModel() {

    val timeRange = MutableLiveData(loadPersistedTimeRange())
    val entries = MutableLiveData<List<TimelineEntry>>(emptyList())
    val selectedIds = MutableLiveData<Set<String>>(emptySet())
    val isMultiSelectMode = MutableLiveData(false)
    val isQuerying = MutableLiveData(false)
    val isBatchRunning = MutableLiveData(false)
    val searchQuery = MutableLiveData("")

    val filteredEntries: LiveData<List<TimelineEntry>> = object : MutableLiveData<List<TimelineEntry>>() {
        private var entriesObserver: androidx.lifecycle.Observer<List<TimelineEntry>>? = null
        private var queryObserver: androidx.lifecycle.Observer<String>? = null

        override fun onActive() {
            entriesObserver = androidx.lifecycle.Observer { applyFilter() }
            queryObserver = androidx.lifecycle.Observer { applyFilter() }
            entries.observeForever(entriesObserver!!)
            searchQuery.observeForever(queryObserver!!)
            applyFilter()
        }

        override fun onInactive() {
            entriesObserver?.let { entries.removeObserver(it) }
            queryObserver?.let { searchQuery.removeObserver(it) }
        }

        private fun applyFilter() {
            val all = entries.value.orEmpty()
            val query = searchQuery.value.orEmpty().trim()
            value = if (query.isEmpty()) all
            else all.filter {
                it.appLabel.contains(query, ignoreCase = true) ||
                it.groupName.contains(query, ignoreCase = true) ||
                it.key.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    private var boundGroupList: LiveData<List<SnapGroup>>? = null
    private var groupListObserver: androidx.lifecycle.Observer<List<SnapGroup>>? = null
    private var timeRangeObserver: androidx.lifecycle.Observer<TimeRange>? = null

    fun bindGroupList(groupList: LiveData<List<SnapGroup>>) {
        boundGroupList?.let { old ->
            groupListObserver?.let { old.removeObserver(it) }
        }
        timeRangeObserver?.let { timeRange.removeObserver(it) }

        groupListObserver = androidx.lifecycle.Observer { requery(groupList, timeRange) }
        timeRangeObserver = androidx.lifecycle.Observer { requery(groupList, timeRange) }

        groupList.observeForever(groupListObserver!!)
        timeRange.observeForever(timeRangeObserver!!)
        boundGroupList = groupList

        requery(groupList, timeRange)
    }

    private fun requery(groupList: LiveData<List<SnapGroup>>, timeRange: LiveData<TimeRange>) {
        val groups = groupList.value ?: emptyList()
        val range = timeRange.value ?: TimelineRepository.defaultLast7Days()
        isQuerying.value = true
        viewModelScope.launch(Dispatchers.Default) {
            val result = TimelineRepository.query(groups, range)
            entries.postValue(result)
            isQuerying.postValue(false)
        }
    }

    fun setTimeRange(range: TimeRange) {
        timeRange.value = range
        clearSelection()
    }

    fun toggleSelection(id: String) {
        val current = selectedIds.value.orEmpty()
        selectedIds.value = if (id in current) current - id else current + id
    }

    fun selectAll() {
        val all = entries.value.orEmpty()
        val query = searchQuery.value.orEmpty().trim()
        val filtered = if (query.isEmpty()) {
            all
        } else {
            all.filter {
                it.appLabel.contains(query, ignoreCase = true) ||
                    it.groupName.contains(query, ignoreCase = true) ||
                    it.key.packageName.contains(query, ignoreCase = true)
            }
        }
        selectedIds.value = filtered.map { it.key.id }.toSet()
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
        isMultiSelectMode.value = false
    }

    fun enterMultiSelectMode() {
        isMultiSelectMode.value = true
    }

    override fun onCleared() {
        super.onCleared()
        boundGroupList?.let { old ->
            groupListObserver?.let { old.removeObserver(it) }
        }
        timeRangeObserver?.let { timeRange.removeObserver(it) }
    }

    companion object {
        fun loadPersistedTimeRange(): TimeRange {
            val presetName = GlobalConfig.timelinePreset
            if (presetName != null) {
                try {
                    val preset = TimePreset.valueOf(presetName)
                    if (preset == TimePreset.CUSTOM) {
                        val start = GlobalConfig.timelineCustomStart
                        val end = GlobalConfig.timelineCustomEnd
                        if (start > 0 && end > start) {
                            return TimeRange(start, end, TimePreset.CUSTOM)
                        }
                    } else {
                        return TimelineRepository.resolveTimeRange(preset)
                    }
                } catch (_: IllegalArgumentException) {}
            }
            return TimelineRepository.defaultLast7Days()
        }
    }
}

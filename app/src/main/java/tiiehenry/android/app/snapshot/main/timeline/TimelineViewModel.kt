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
    private var isBound = false
    private var activeQueryRange: TimeRange? = null

    fun bindGroupList(groupList: LiveData<List<SnapGroup>>) {
        if (isBound && boundGroupList === groupList) {
            requery(groupList)
            return
        }
        unbindGroupList()
        groupListObserver = androidx.lifecycle.Observer { requery(groupList) }
        timeRangeObserver = androidx.lifecycle.Observer { requery(groupList) }
        groupList.observeForever(groupListObserver!!)
        timeRange.observeForever(timeRangeObserver!!)
        boundGroupList = groupList
        isBound = true
        requery(groupList)
    }

    fun unbindGroupList() {
        boundGroupList?.let { old ->
            groupListObserver?.let { old.removeObserver(it) }
        }
        timeRangeObserver?.let { timeRange.removeObserver(it) }
        groupListObserver = null
        timeRangeObserver = null
        boundGroupList = null
        isBound = false
    }

    /**
     * 列表、热力图和批量恢复、删除、导出共用最近一次查询的有效区间。
     * 最近 7/30 天的右端在 [requery] 里重算，不写回 [timeRange]。
     */
    fun effectiveTimeRange(): TimeRange = activeQueryRange ?: computeEffectiveQueryRange()

    private fun computeEffectiveQueryRange(): TimeRange {
        val stored = timeRange.value ?: return TimelineRepository.defaultLast7Days()
        return effectiveQueryRange(stored)
    }

    private fun requery(groupList: LiveData<List<SnapGroup>>) {
        val groups = groupList.value ?: emptyList()
        val range = computeEffectiveQueryRange()
        activeQueryRange = range
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
        unbindGroupList()
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

internal fun effectiveQueryRange(stored: TimeRange): TimeRange {
    return when (stored.preset) {
        TimePreset.LAST_7_DAYS, TimePreset.LAST_30_DAYS ->
            TimelineRepository.resolveTimeRange(stored.preset)
        TimePreset.TODAY, TimePreset.YESTERDAY, TimePreset.CUSTOM -> stored
    }
}

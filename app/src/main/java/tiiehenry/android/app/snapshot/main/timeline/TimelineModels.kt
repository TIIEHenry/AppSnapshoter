package tiiehenry.android.app.snapshot.main.timeline

data class TimelineEntryKey(
    val groupId: String,
    val packageName: String,
    val userId: Int
) {
    val id: String get() = "$groupId:$packageName:$userId"
}

data class TimelineEntry(
    val key: TimelineEntryKey,
    val appLabel: String,
    val groupName: String,
    val iconFile: String,
    val matchingArchiveNames: List<String>,
    val matchingArchiveTimes: List<Long>
)

enum class TimePreset { TODAY, YESTERDAY, LAST_7_DAYS, LAST_30_DAYS, CUSTOM }

data class TimeRange(
    val startTime: Long,
    val endTimeExclusive: Long,
    val preset: TimePreset = TimePreset.LAST_7_DAYS
)

enum class RestoreStrategy {
    NEWEST_FIRST,
    OLDEST_FIRST
}

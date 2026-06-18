package tiiehenry.android.app.snapshot.main.timeline

import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.main.launch.batch.ArchiveResolver
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object TimelineRepository {

    fun query(
        groupList: List<SnapGroup>,
        range: TimeRange
    ): List<TimelineEntry> {
        return groupList.flatMap { group ->
            group.apps.mapNotNull { app ->
                val matching = synchronized(app.archives) {
                    app.archives.values
                        .filter { it.metaInfo.makeTime in range.startTime until range.endTimeExclusive }
                        .sortedByDescending { it.metaInfo.makeTime }
                }
                if (matching.isEmpty()) return@mapNotNull null
                TimelineEntry(
                    key = TimelineEntryKey(group.id, app.appInfo.packageName, app.appInfo.userId),
                    appLabel = app.appInfo.label,
                    groupName = group.name,
                    iconFile = app.appInfo.archiveIconFile ?: app.iconFile,
                    matchingArchiveNames = matching.map { it.name },
                    matchingArchiveTimes = matching.map { it.metaInfo.makeTime }
                )
            }
        }.sortedByDescending { it.matchingArchiveTimes.first() }
    }

    fun defaultLast7Days(): TimeRange {
        val now = System.currentTimeMillis()
        return TimeRange(
            startTime = now - 7L * 24 * 60 * 60 * 1000,
            endTimeExclusive = now,
            preset = TimePreset.LAST_7_DAYS
        )
    }

    fun resolveTimeRange(preset: TimePreset): TimeRange {
        val zone = ZoneId.systemDefault()
        val now = Instant.now().toEpochMilli()
        return when (preset) {
            TimePreset.TODAY -> {
                val start = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
                val end = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                TimeRange(start, end, preset)
            }
            TimePreset.YESTERDAY -> {
                val start = LocalDate.now().minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
                TimeRange(start, end, preset)
            }
            TimePreset.LAST_7_DAYS -> {
                TimeRange(now - 7L * 24 * 60 * 60 * 1000, now, preset)
            }
            TimePreset.LAST_30_DAYS -> {
                TimeRange(now - 30L * 24 * 60 * 60 * 1000, now, preset)
            }
            TimePreset.CUSTOM -> defaultLast7Days()
        }
    }

    fun resolveArchive(
        archives: List<ArchiveItem>,
        strategy: RestoreStrategy
    ): ArchiveItem = ArchiveResolver.pick(archives, strategy)

    fun resolveEntry(
        key: TimelineEntryKey,
        groups: List<SnapGroup>,
        range: TimeRange
    ): Pair<ArchivedApp, List<ArchiveItem>>? {
        val group = groups.find { it.id == key.groupId } ?: return null
        val app = group.apps.find {
            it.appInfo.packageName == key.packageName && it.appInfo.userId == key.userId
        } ?: return null
        val matching = synchronized(app.archives) {
            app.archives.values
                .filter { it.metaInfo.makeTime in range.startTime until range.endTimeExclusive }
                .sortedByDescending { it.metaInfo.makeTime }
        }
        if (matching.isEmpty()) return null
        return app to matching
    }
}

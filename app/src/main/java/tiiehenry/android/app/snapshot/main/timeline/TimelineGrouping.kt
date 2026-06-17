package tiiehenry.android.app.snapshot.main.timeline

import android.content.Context
import tiiehenry.android.app.snapshot.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimelineGrouping {

  private val dateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.getDefault())

  fun groupEntries(entries: List<TimelineEntry>, context: Context): List<TimelineListItem> {
    if (entries.isEmpty()) return emptyList()

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val yesterday = today.minusDays(1)

    val grouped = entries.groupBy { entry ->
      val latestTime = entry.matchingArchiveTimes.maxOrNull() ?: 0L
      Instant.ofEpochMilli(latestTime).atZone(zone).toLocalDate()
    }.entries.sortedByDescending { it.key }

    return grouped.flatMap { (date, groupEntries) ->
      val label = when (date) {
        today -> context.getString(R.string.timeline_today)
        yesterday -> context.getString(R.string.timeline_yesterday)
        else -> date.format(dateFormatter)
      }
      val header = TimelineListItem.DateHeader(label, date.toEpochDay())
      val sortedEntries = groupEntries.sortedByDescending { it.matchingArchiveTimes.maxOrNull() ?: 0L }
      listOf(header) + sortedEntries.map { TimelineListItem.Entry(it) }
    }
  }
}

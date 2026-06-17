package tiiehenry.android.app.snapshot.main.timeline

sealed class TimelineListItem {
    data class DateHeader(val label: String, val epochDay: Long) : TimelineListItem()
    data class Entry(val entry: TimelineEntry) : TimelineListItem()
}

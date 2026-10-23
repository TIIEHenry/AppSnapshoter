package tiiehenry.android.app.snapshot.main.timeline

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

internal object TimelineDateRanges {
    /** MaterialDatePicker encodes selected calendar dates at midnight UTC. */
    fun fromPickerSelection(startUtcMillis: Long, endUtcMillis: Long, zone: ZoneId): TimeRange =
        fromDates(
            Instant.ofEpochMilli(startUtcMillis).atZone(ZoneOffset.UTC).toLocalDate(),
            Instant.ofEpochMilli(endUtcMillis).atZone(ZoneOffset.UTC).toLocalDate(),
            zone,
        )

    fun fromDates(start: LocalDate, endInclusive: LocalDate, zone: ZoneId): TimeRange =
        TimeRange(
            startTime = start.atStartOfDay(zone).toInstant().toEpochMilli(),
            endTimeExclusive = endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            preset = TimePreset.CUSTOM,
        )
}

package tiiehenry.android.app.snapshot.main.timeline

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TimelineDateRangesTest {
    private fun millis(instant: String) = Instant.parse(instant).toEpochMilli()

    @Test
    fun `Shanghai selection includes local midnight and excludes next local day`() {
        val range = TimelineDateRanges.fromPickerSelection(
            millis("2026-10-23T00:00:00Z"),
            millis("2026-10-23T00:00:00Z"),
            ZoneId.of("Asia/Shanghai"),
        )

        assertEquals(millis("2026-10-22T16:00:00Z"), range.startTime)
        assertEquals(millis("2026-10-23T16:00:00Z"), range.endTimeExclusive)
        assertEquals(TimePreset.CUSTOM, range.preset)
        assertTrue(millis("2026-10-22T17:00:00Z") in range.startTime until range.endTimeExclusive)
        assertFalse(millis("2026-10-23T17:00:00Z") in range.startTime until range.endTimeExclusive)
    }

    @Test
    fun `negative time zone retains the date selected in the picker`() {
        val range = TimelineDateRanges.fromPickerSelection(
            millis("2026-10-23T00:00:00Z"),
            millis("2026-10-23T00:00:00Z"),
            ZoneId.of("America/Los_Angeles"),
        )

        assertEquals(millis("2026-10-23T07:00:00Z"), range.startTime)
        assertEquals(millis("2026-10-24T07:00:00Z"), range.endTimeExclusive)
    }

    @Test
    fun `spring daylight saving day spans 23 hours`() {
        val range = TimelineDateRanges.fromPickerSelection(
            millis("2026-03-08T00:00:00Z"),
            millis("2026-03-08T00:00:00Z"),
            ZoneId.of("America/New_York"),
        )

        assertEquals(millis("2026-03-08T05:00:00Z"), range.startTime)
        assertEquals(millis("2026-03-09T04:00:00Z"), range.endTimeExclusive)
        assertEquals(23 * 60 * 60 * 1000L, range.endTimeExclusive - range.startTime)
    }

    @Test
    fun `autumn daylight saving day spans 25 hours`() {
        val range = TimelineDateRanges.fromPickerSelection(
            millis("2026-11-01T00:00:00Z"),
            millis("2026-11-01T00:00:00Z"),
            ZoneId.of("America/New_York"),
        )

        assertEquals(millis("2026-11-01T04:00:00Z"), range.startTime)
        assertEquals(millis("2026-11-02T05:00:00Z"), range.endTimeExclusive)
        assertEquals(25 * 60 * 60 * 1000L, range.endTimeExclusive - range.startTime)
    }

    @Test
    fun `range includes the entire final selected date`() {
        val range = TimelineDateRanges.fromPickerSelection(
            millis("2026-10-21T00:00:00Z"),
            millis("2026-10-23T00:00:00Z"),
            ZoneId.of("Asia/Shanghai"),
        )

        assertEquals(millis("2026-10-20T16:00:00Z"), range.startTime)
        assertEquals(millis("2026-10-23T16:00:00Z"), range.endTimeExclusive)
    }
}

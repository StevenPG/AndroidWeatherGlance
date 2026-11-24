package com.stevenpg.weatherglance.scheduling

import com.stevenpg.weatherglance.data.settings.NotificationTime
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScheduleCalculatorTest {

    private val zone = ZoneId.of("America/New_York")
    private val everyDay = DayOfWeek.entries.toSet()
    private val twiceDaily = listOf(NotificationTime(17, 0), NotificationTime(7, 0))

    private fun at(text: String) = ZonedDateTime.of(java.time.LocalDateTime.parse(text), zone)

    @Test
    fun `picks the next time later today`() {
        assertEquals(at("2026-11-24T17:00"), next(after = at("2026-11-24T08:15")))
    }

    @Test
    fun `rolls over to tomorrow's first slot`() {
        assertEquals(at("2026-11-25T07:00"), next(after = at("2026-11-24T17:00")))
    }

    @Test
    fun `slot equal to now is not reused`() {
        assertEquals(at("2026-11-24T17:00"), next(after = at("2026-11-24T07:00")))
    }

    @Test
    fun `skips inactive days`() {
        val weekdays = everyDay - DayOfWeek.SATURDAY - DayOfWeek.SUNDAY
        // Friday 2026-11-27 evening → Monday morning.
        assertEquals(at("2026-11-30T07:00"), next(after = at("2026-11-27T18:00"), days = weekdays))
    }

    @Test
    fun `single active day can be a week out`() {
        val result = next(after = at("2026-11-24T07:30"), days = setOf(DayOfWeek.TUESDAY))
        assertEquals(at("2026-11-24T17:00"), result)
        assertEquals(at("2026-12-01T07:00"), next(after = at("2026-11-24T17:30"), days = setOf(DayOfWeek.TUESDAY)))
    }

    @Test
    fun `spring forward gap shifts slot by the gap`() {
        // 2026-03-08 02:30 does not exist in New York; it becomes 03:30 EDT.
        val result = ScheduleCalculator.nextOccurrence(
            listOf(NotificationTime(2, 30)),
            everyDay,
            at("2026-03-08T00:00"),
        )
        assertEquals(ZonedDateTime.parse("2026-03-08T03:30-04:00[America/New_York]"), result)
    }

    @Test
    fun `fall back overlap fires once using the earlier offset`() {
        val times = listOf(NotificationTime(1, 30))
        val first = ScheduleCalculator.nextOccurrence(times, everyDay, at("2026-11-01T00:00"))
        assertEquals(ZonedDateTime.parse("2026-11-01T01:30-04:00[America/New_York]"), first)
        // After the first 01:30 has passed, the next slot is tomorrow — not the repeated 01:30 EST.
        val second = ScheduleCalculator.nextOccurrence(times, everyDay, requireNotNull(first))
        assertEquals(ZonedDateTime.parse("2026-11-02T01:30-05:00[America/New_York]"), second)
    }

    @Test
    fun `wall-clock time survives DST change across days`() {
        // Saturday 18:00 EDT → Sunday 07:00 EST (25 hours later in elapsed time).
        assertEquals(
            ZonedDateTime.parse("2026-11-01T07:00-05:00[America/New_York]"),
            next(after = at("2026-10-31T18:00")),
        )
    }

    @Test
    fun `nothing to schedule without times or days`() {
        assertNull(ScheduleCalculator.nextOccurrence(emptyList(), everyDay, at("2026-11-24T08:00")))
        assertNull(ScheduleCalculator.nextOccurrence(twiceDaily, emptySet(), at("2026-11-24T08:00")))
    }

    private fun next(after: ZonedDateTime, days: Set<DayOfWeek> = everyDay) =
        ScheduleCalculator.nextOccurrence(twiceDaily, days, after)
}

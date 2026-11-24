package com.stevenpg.weatherglance.scheduling

import com.stevenpg.weatherglance.data.settings.NotificationTime
import java.time.DayOfWeek
import java.time.ZonedDateTime

object ScheduleCalculator {

    private const val DAYS_TO_SEARCH = 8L

    /**
     * The first configured wall-clock slot strictly after [after], in [after]'s zone.
     *
     * DST is handled by [ZonedDateTime.of]: a slot inside a spring-forward gap moves forward by the
     * gap length (02:30 becomes 03:30), and a slot in a fall-back overlap uses the earlier offset, so
     * it fires once rather than twice.
     */
    fun nextOccurrence(
        times: Collection<NotificationTime>,
        activeDays: Set<DayOfWeek>,
        after: ZonedDateTime,
    ): ZonedDateTime? {
        if (times.isEmpty() || activeDays.isEmpty()) return null
        val sortedTimes = times.sorted()
        for (offset in 0 until DAYS_TO_SEARCH) {
            val date = after.toLocalDate().plusDays(offset)
            if (date.dayOfWeek !in activeDays) continue
            for (time in sortedTimes) {
                val candidate = ZonedDateTime.of(date, time.toLocalTime(), after.zone)
                if (candidate.isAfter(after)) return candidate
            }
        }
        return null
    }
}

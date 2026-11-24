package com.stevenpg.weatherglance.data.settings

import com.stevenpg.weatherglance.domain.model.AlertSeverity
import com.stevenpg.weatherglance.domain.model.SavedLocation
import com.stevenpg.weatherglance.domain.model.TemperatureUnit
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalTime

@Serializable
data class NotificationTime(val hour: Int, val minute: Int) : Comparable<NotificationTime> {
    init {
        require(hour in 0..MAX_HOUR) { "hour out of range: $hour" }
        require(minute in 0..MAX_MINUTE) { "minute out of range: $minute" }
    }

    fun toLocalTime(): LocalTime = LocalTime.of(hour, minute)

    override fun compareTo(other: NotificationTime): Int = compareValuesBy(this, other, { it.hour }, { it.minute })

    private companion object {
        const val MAX_HOUR = 23
        const val MAX_MINUTE = 59
    }
}

@Serializable
data class AppSettings(
    val location: SavedLocation? = null,
    val notificationsEnabled: Boolean = true,
    val notificationTimes: List<NotificationTime> = DEFAULT_TIMES,
    /** ISO-8601 day numbers (Monday = 1). Stored as ints to keep the on-disk format stable. */
    val activeDays: Set<Int> = ALL_DAYS,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    val alertsEnabled: Boolean = true,
    val minAlertSeverity: AlertSeverity = AlertSeverity.SEVERE,
) {
    val activeDaysOfWeek: Set<DayOfWeek> get() = activeDays.map(DayOfWeek::of).toSet()

    companion object {
        val DEFAULT_TIMES = listOf(NotificationTime(7, 0), NotificationTime(17, 0))
        val ALL_DAYS = DayOfWeek.entries.map { it.value }.toSet()
        const val MAX_NOTIFICATION_TIMES = 6
    }
}

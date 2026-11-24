package com.stevenpg.weatherglance.domain.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Serializable
data class SavedLocation(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    /** NWS gridpoint forecast URL resolved from `/points`. */
    val forecastUrl: String,
    /** IANA zone of the forecast location (not necessarily the device's zone). */
    val timeZoneId: String,
) {
    fun zoneId(): ZoneId = runCatching { ZoneId.of(timeZoneId) }.getOrDefault(ZoneId.systemDefault())

    fun hasSameCoordinates(other: SavedLocation?): Boolean =
        other != null && other.latitude == latitude && other.longitude == longitude
}

@Serializable
data class PeriodDetail(val name: String, val text: String)

@Serializable
data class DailyForecast(
    @Serializable(with = LocalDateIsoSerializer::class)
    val date: LocalDate,
    /** True when only the overnight period remains for this date (e.g. fetched in the evening). */
    val isNightOnly: Boolean,
    val condition: WeatherCondition,
    val highF: Int?,
    val lowF: Int?,
    /** Chance of precipitation, 0-100, for the primary (daytime when available) period. */
    val precipChance: Int?,
    val shortForecast: String,
    val details: List<PeriodDetail> = emptyList(),
)

@Serializable
data class ForecastSnapshot(
    val location: SavedLocation,
    @Serializable(with = InstantEpochMillisSerializer::class)
    val fetchedAt: Instant,
    val days: List<DailyForecast>,
    val alerts: List<WeatherAlert> = emptyList(),
) {
    /** Days that have not already passed in the forecast location's local calendar. */
    fun daysFrom(today: LocalDate): List<DailyForecast> = days.filter { !it.date.isBefore(today) }
}

package com.stevenpg.weatherglance.data.nws

import com.stevenpg.weatherglance.domain.model.AlertSeverity
import com.stevenpg.weatherglance.domain.model.DailyForecast
import com.stevenpg.weatherglance.domain.model.PeriodDetail
import com.stevenpg.weatherglance.domain.model.WeatherAlert
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

object NwsMapper {

    /**
     * Collapses NWS's alternating day/night periods into one entry per calendar date.
     *
     * Period timestamps carry the forecast location's UTC offset, so grouping by the local date of
     * `startTime` yields days in the *location's* calendar. The daytime period supplies the high,
     * icon and precipitation chance; the evening period supplies the low. A date with only a night
     * period (the forecast was fetched in the evening) becomes a "tonight" entry.
     */
    fun toDailyForecasts(periods: List<ForecastPeriod>): List<DailyForecast> =
        periods
            .mapNotNull { period -> parseOffsetDateTime(period.startTime)?.let { it.toLocalDate() to period } }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .map { (date, datePeriods) ->
                val day = datePeriods.firstOrNull { it.isDaytime }
                // Use the *last* night period so an early-morning "Overnight" doesn't masquerade as tonight's low.
                val night = datePeriods.lastOrNull { !it.isDaytime }
                val primary = day ?: datePeriods.first()
                DailyForecast(
                    date = date,
                    isNightOnly = day == null,
                    condition = WeatherConditionParser.parse(primary.icon, primary.shortForecast),
                    highF = day?.temperature?.roundToInt(),
                    lowF = night?.temperature?.roundToInt(),
                    precipChance = primary.probabilityOfPrecipitation?.value?.roundToInt(),
                    shortForecast = primary.shortForecast,
                    details = listOfNotNull(day, night)
                        .filter { it.detailedForecast.isNotBlank() }
                        .map { PeriodDetail(it.name, it.detailedForecast) },
                )
            }
            .sortedBy { it.date }

    fun toAlerts(response: AlertsResponse, now: Instant): List<WeatherAlert> =
        response.features
            .map { it.properties }
            // Exclude "Test", "Exercise", "System" and "Draft" messages.
            .filter { it.status == null || it.status.equals("Actual", ignoreCase = true) }
            .mapNotNull { props ->
                val endsAt = parseInstant(props.ends) ?: parseInstant(props.expires)
                if (endsAt != null && endsAt.isBefore(now)) {
                    null
                } else {
                    WeatherAlert(
                        id = props.id,
                        event = props.event,
                        headline = props.headline,
                        severity = AlertSeverity.fromNws(props.severity),
                        endsAt = endsAt,
                        description = props.description?.trim(),
                        instruction = props.instruction?.trim(),
                    )
                }
            }
            .distinctBy { it.id }
            .sortedByDescending { it.severity }

    private fun parseOffsetDateTime(value: String): OffsetDateTime? = try {
        OffsetDateTime.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun parseInstant(value: String?): Instant? = value?.let { parseOffsetDateTime(it)?.toInstant() }
}

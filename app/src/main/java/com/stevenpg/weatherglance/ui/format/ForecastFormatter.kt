package com.stevenpg.weatherglance.ui.format

import android.content.Context
import android.text.format.DateFormat
import com.stevenpg.weatherglance.R
import com.stevenpg.weatherglance.domain.model.DailyForecast
import com.stevenpg.weatherglance.domain.model.TemperatureUnit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Shared display formatting for the notification, widget and app UI. */
class ForecastFormatter(
    private val context: Context,
    private val unit: TemperatureUnit,
    private val today: LocalDate,
    private val locale: Locale = Locale.getDefault(),
) {
    fun dayLabel(day: DailyForecast): String = when {
        day.date == today && day.isNightOnly -> context.getString(R.string.label_tonight)
        day.date == today -> context.getString(R.string.label_today)
        else -> day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    }

    fun longDayLabel(day: DailyForecast): String = when {
        day.date == today -> dayLabel(day)
        else -> day.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
    }

    fun temperature(fahrenheit: Int?): String =
        fahrenheit?.let { context.getString(R.string.temperature_format, unit.fromFahrenheit(it)) }
            ?: context.getString(R.string.temperature_missing)

    fun highLow(day: DailyForecast): String =
        context.getString(R.string.high_low_format, temperature(day.highF), temperature(day.lowF))

    /** Precipitation chance as "40%", or null below [MIN_PRECIP_TO_SHOW] where it is just noise. */
    fun precip(day: DailyForecast): String? =
        day.precipChance?.takeIf { it >= MIN_PRECIP_TO_SHOW }?.let { context.getString(R.string.precip_format, it) }

    fun summary(day: DailyForecast): String =
        context.getString(R.string.summary_format, dayLabel(day), day.shortForecast, highLow(day))

    fun contentDescription(day: DailyForecast): String = buildString {
        append(longDayLabel(day)).append(", ").append(day.shortForecast)
        day.highF?.let { append(", ").append(context.getString(R.string.cd_high, temperature(it))) }
        day.lowF?.let { append(", ").append(context.getString(R.string.cd_low, temperature(it))) }
        day.precipChance?.takeIf { it > 0 }?.let { chance ->
            append(", ").append(context.resources.getQuantityString(R.plurals.cd_precip, chance, chance))
        }
    }

    fun time(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        timeFormatter(context).format(instant.atZone(zone))

    /** "6:00 PM" when [instant] falls on [today] in [zone], otherwise "Thu 6:00 PM". */
    fun dayTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val dateTime = instant.atZone(zone)
        val time = timeFormatter(context).format(dateTime)
        return if (dateTime.toLocalDate() == today) {
            time
        } else {
            "${dateTime.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)} $time"
        }
    }

    fun dayAndTime(dateTime: ZonedDateTime): String {
        val day = when (dateTime.toLocalDate()) {
            LocalDate.now(dateTime.zone) -> context.getString(R.string.label_today)
            LocalDate.now(dateTime.zone).plusDays(1) -> context.getString(R.string.label_tomorrow)
            else -> dateTime.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        }
        return context.getString(R.string.day_time_format, day, timeFormatter(context).format(dateTime))
    }

    companion object {
        const val MIN_PRECIP_TO_SHOW = 10

        fun timeFormatter(context: Context): DateTimeFormatter =
            DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a")
    }
}

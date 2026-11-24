package com.stevenpg.weatherglance.data.nws

import com.stevenpg.weatherglance.TestFixtures
import com.stevenpg.weatherglance.domain.model.AlertSeverity
import com.stevenpg.weatherglance.domain.model.WeatherCondition
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NwsMapperTest {

    private val forecast: ForecastResponse =
        TestFixtures.json.decodeFromString(TestFixtures.read("nws/forecast.json"))
    private val alerts: AlertsResponse =
        TestFixtures.json.decodeFromString(TestFixtures.read("nws/alerts.json"))

    @Test
    fun `real NWS forecast collapses into one entry per local date`() {
        val days = NwsMapper.toDailyForecasts(forecast.properties.periods)

        assertEquals(7, days.size)
        assertEquals((0L..6L).map { LocalDate.of(2026, 11, 24).plusDays(it) }, days.map { it.date })

        val today = days[0]
        assertEquals(43, today.highF)
        assertEquals(25, today.lowF)
        assertEquals(WeatherCondition.MOSTLY_CLOUDY, today.condition)
        assertEquals(false, today.isNightOnly)
        assertEquals(listOf("This Afternoon", "Tonight"), today.details.map { it.name })

        val wednesday = days[1]
        assertEquals(42, wednesday.highF)
        assertEquals(29, wednesday.lowF)
        assertEquals(30, wednesday.precipChance)
        assertEquals(WeatherCondition.SNOW, wednesday.condition, "bkn/snow should surface the snow")

        val sunday = days[5]
        assertEquals(WeatherCondition.RAIN, sunday.condition)
    }

    @Test
    fun `evening fetch yields a night-only first day`() {
        val periods = listOf(
            period("Tonight", "2026-11-24T18:00:00-06:00", daytime = false, temp = 25.0, icon = "night/skc"),
            period("Wednesday", "2026-11-25T06:00:00-06:00", daytime = true, temp = 42.0, icon = "day/sct"),
            period("Wednesday Night", "2026-11-25T18:00:00-06:00", daytime = false, temp = 29.0, icon = "night/sct"),
        )

        val days = NwsMapper.toDailyForecasts(periods)

        assertEquals(2, days.size)
        assertTrue(days[0].isNightOnly)
        assertNull(days[0].highF)
        assertEquals(25, days[0].lowF)
        assertEquals(WeatherCondition.CLEAR, days[0].condition)
    }

    @Test
    fun `overnight period does not replace tonight's low`() {
        val periods = listOf(
            period("Overnight", "2026-11-24T02:00:00-06:00", daytime = false, temp = 20.0, icon = "night/skc"),
            period("Today", "2026-11-24T06:00:00-06:00", daytime = true, temp = 45.0, icon = "day/skc"),
            period("Tonight", "2026-11-24T18:00:00-06:00", daytime = false, temp = 27.0, icon = "night/skc"),
        )

        val today = NwsMapper.toDailyForecasts(periods).single()

        assertEquals(45, today.highF)
        assertEquals(27, today.lowF)
    }

    @Test
    fun `unparseable timestamps are skipped rather than crashing`() {
        val periods = listOf(
            period("Bad", "not-a-time", daytime = true, temp = 1.0, icon = "day/skc"),
            period("Today", "2026-11-24T06:00:00-06:00", daytime = true, temp = 45.0, icon = "day/skc"),
        )
        assertEquals(1, NwsMapper.toDailyForecasts(periods).size)
    }

    @Test
    fun `active alerts are mapped with severity and end time`() {
        val mapped = NwsMapper.toAlerts(alerts, Instant.parse("2026-11-24T19:00:00Z"))

        val alert = mapped.single()
        assertEquals("Winter Storm Watch", alert.event)
        assertEquals(AlertSeverity.SEVERE, alert.severity)
        assertEquals(Instant.parse("2026-11-27T00:00:00Z"), alert.endsAt)
    }

    @Test
    fun `ended and non-actual alerts are dropped`() {
        assertTrue(NwsMapper.toAlerts(alerts, Instant.parse("2026-11-28T00:00:00Z")).isEmpty())

        val test = AlertsResponse(
            listOf(AlertFeature(AlertProperties(id = "x", event = "Test Message", status = "Test"))),
        )
        assertTrue(NwsMapper.toAlerts(test, Instant.parse("2026-11-24T00:00:00Z")).isEmpty())
    }

    private fun period(name: String, start: String, daytime: Boolean, temp: Double, icon: String) = ForecastPeriod(
        name = name,
        startTime = start,
        isDaytime = daytime,
        temperature = temp,
        icon = "https://api.weather.gov/icons/land/$icon?size=medium",
        shortForecast = name,
    )
}

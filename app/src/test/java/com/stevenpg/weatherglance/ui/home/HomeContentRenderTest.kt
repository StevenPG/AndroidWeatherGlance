package com.stevenpg.weatherglance.ui.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.stevenpg.weatherglance.TestFixtures
import com.stevenpg.weatherglance.data.nws.AlertsResponse
import com.stevenpg.weatherglance.data.nws.ForecastResponse
import com.stevenpg.weatherglance.data.nws.NwsMapper
import com.stevenpg.weatherglance.domain.model.ForecastSnapshot
import com.stevenpg.weatherglance.domain.model.SavedLocation
import com.stevenpg.weatherglance.domain.model.TemperatureUnit
import com.stevenpg.weatherglance.ui.theme.WeatherGlanceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h1400dp-xhdpi")
class HomeContentRenderTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val now: Instant = Instant.now()
    private val location = SavedLocation(
        displayName = "Springfield, IL",
        latitude = 39.7817,
        longitude = -89.6501,
        forecastUrl = "https://api.weather.gov/gridpoints/ILX/47,55/forecast",
        timeZoneId = "America/Chicago",
    )

    /** Re-dates the recorded forecast so its first day is "today" whenever the test runs. */
    private val snapshot: ForecastSnapshot by lazy {
        val days = NwsMapper.toDailyForecasts(
            TestFixtures.json.decodeFromString<ForecastResponse>(TestFixtures.read("nws/forecast.json"))
                .properties.periods,
        )
        val shift = java.time.temporal.ChronoUnit.DAYS.between(
            days.first().date,
            java.time.LocalDate.now(location.zoneId()),
        )
        ForecastSnapshot(
            location = location,
            fetchedAt = now,
            days = days.map { it.copy(date = it.date.plusDays(shift)) },
            alerts = NwsMapper.toAlerts(
                TestFixtures.json.decodeFromString<AlertsResponse>(TestFixtures.read("nws/alerts.json")),
                Instant.parse("2026-11-24T19:00:00Z"),
            ),
        )
    }

    private fun state(unit: TemperatureUnit = TemperatureUnit.FAHRENHEIT) = HomeUiState(
        loading = false,
        location = location,
        snapshot = snapshot,
        unit = unit,
        alerts = snapshot.alerts,
        nextNotification = ZonedDateTime.now(ZoneId.systemDefault()).plusHours(3),
    )

    @Test
    fun `forecast home renders location, alert and notify action`() {
        var notified = 0
        composeRule.setContent {
            WeatherGlanceTheme(darkTheme = false) {
                HomeContent(state(), SnackbarHostState(), {}, {}, {}, { notified++ })
            }
        }
        composeRule.onNodeWithText("Springfield, IL").assertIsDisplayed()
        composeRule.onNodeWithText("Winter Storm Watch").assertIsDisplayed()
        composeRule.onNodeWithText("Notify now").performClick()
        assertEquals(1, notified)
        save("home-light")
    }

    @Test
    fun `empty state prompts for a location`() {
        composeRule.setContent {
            WeatherGlanceTheme(darkTheme = true) {
                HomeContent(HomeUiState(loading = false), SnackbarHostState(), {}, {}, {}, {})
            }
        }
        composeRule.onNodeWithText("Choose a location").assertIsDisplayed()
        save("home-empty-dark")
    }

    @Test
    fun `dark theme with celsius`() {
        composeRule.setContent {
            WeatherGlanceTheme(darkTheme = true) {
                HomeContent(state(TemperatureUnit.CELSIUS), SnackbarHostState(), {}, {}, {}, {})
            }
        }
        save("home-dark-celsius")
    }

    private fun save(name: String) {
        composeRule.waitForIdle()
        val view = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("build/notification-renders").apply { mkdirs() }.resolve("$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

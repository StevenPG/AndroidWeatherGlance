package com.stevenpg.weatherglance.data.settings

import com.stevenpg.weatherglance.TestFixtures
import com.stevenpg.weatherglance.domain.model.AlertSeverity
import com.stevenpg.weatherglance.domain.model.SavedLocation
import com.stevenpg.weatherglance.domain.model.TemperatureUnit
import org.junit.Test
import kotlin.test.assertEquals

class AppSettingsSerializationTest {

    @Test
    fun `settings round-trip through JSON`() {
        val settings = AppSettings(
            location = SavedLocation(
                "Springfield, IL",
                39.7817,
                -89.6501,
                "https://example/forecast",
                "America/Chicago",
            ),
            notificationTimes = listOf(NotificationTime(6, 45), NotificationTime(18, 0)),
            activeDays = setOf(1, 2, 3, 4, 5),
            temperatureUnit = TemperatureUnit.CELSIUS,
            minAlertSeverity = AlertSeverity.EXTREME,
        )
        val json = TestFixtures.json.encodeToString(AppSettings.serializer(), settings)
        assertEquals(settings, TestFixtures.json.decodeFromString(AppSettings.serializer(), json))
    }

    @Test
    fun `older or partial files fall back to defaults for missing fields`() {
        val decoded = TestFixtures.json.decodeFromString(AppSettings.serializer(), """{"notificationsEnabled":false}""")
        assertEquals(AppSettings(notificationsEnabled = false), decoded)
    }

    @Test
    fun `unknown enum values are coerced to defaults instead of corrupting settings`() {
        val decoded = TestFixtures.json.decodeFromString(AppSettings.serializer(), """{"temperatureUnit":"KELVIN"}""")
        assertEquals(TemperatureUnit.FAHRENHEIT, decoded.temperatureUnit)
    }
}

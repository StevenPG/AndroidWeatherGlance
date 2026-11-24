package com.stevenpg.weatherglance.domain

import com.stevenpg.weatherglance.domain.model.TemperatureUnit
import org.junit.Test
import kotlin.test.assertEquals

class TemperatureUnitTest {
    @Test
    fun `fahrenheit passes through`() = assertEquals(72, TemperatureUnit.FAHRENHEIT.fromFahrenheit(72))

    @Test
    fun `celsius conversion rounds to nearest degree`() {
        assertEquals(0, TemperatureUnit.CELSIUS.fromFahrenheit(32))
        assertEquals(100, TemperatureUnit.CELSIUS.fromFahrenheit(212))
        assertEquals(22, TemperatureUnit.CELSIUS.fromFahrenheit(72))
        assertEquals(-40, TemperatureUnit.CELSIUS.fromFahrenheit(-40))
        assertEquals(-4, TemperatureUnit.CELSIUS.fromFahrenheit(25))
    }
}

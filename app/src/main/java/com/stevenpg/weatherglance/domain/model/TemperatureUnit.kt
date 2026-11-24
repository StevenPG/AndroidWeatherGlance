package com.stevenpg.weatherglance.domain.model

import kotlin.math.roundToInt

enum class TemperatureUnit {
    FAHRENHEIT,
    CELSIUS,
    ;

    /** NWS forecasts are fetched in Fahrenheit; convert at display time so toggling needs no refetch. */
    fun fromFahrenheit(fahrenheit: Int): Int = when (this) {
        FAHRENHEIT -> fahrenheit
        CELSIUS -> ((fahrenheit - FREEZING_F) * CELSIUS_PER_FAHRENHEIT).roundToInt()
    }

    private companion object {
        const val FREEZING_F = 32
        const val CELSIUS_PER_FAHRENHEIT = 5.0 / 9.0
    }
}

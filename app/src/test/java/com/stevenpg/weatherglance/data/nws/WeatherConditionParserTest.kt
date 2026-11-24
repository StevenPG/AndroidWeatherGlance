package com.stevenpg.weatherglance.data.nws

import com.stevenpg.weatherglance.domain.model.WeatherCondition
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WeatherConditionParserTest {

    @Test
    fun `single icon code maps directly`() {
        assertEquals(
            WeatherCondition.MOSTLY_CLOUDY,
            WeatherConditionParser.fromIconUrl("https://api.weather.gov/icons/land/day/bkn?size=medium"),
        )
    }

    @Test
    fun `dual icon codes pick the more significant condition`() {
        // "Mostly Cloudy then Chance Rain And Snow"
        assertEquals(
            WeatherCondition.SNOW,
            WeatherConditionParser.fromIconUrl("https://api.weather.gov/icons/land/day/bkn/snow,30?size=medium"),
        )
        assertEquals(
            WeatherCondition.THUNDERSTORM,
            WeatherConditionParser.fromIconUrl("https://api.weather.gov/icons/land/night/tsra_hi,40/rain,20"),
        )
    }

    @Test
    fun `percentage suffixes and night sets are handled`() {
        assertEquals(
            WeatherCondition.RAIN_SHOWERS,
            WeatherConditionParser.fromIconUrl("https://api.weather.gov/icons/land/night/rain_showers,60?size=small"),
        )
    }

    @Test
    fun `unknown icon falls back to short forecast text`() {
        assertEquals(
            WeatherCondition.FOG,
            WeatherConditionParser.parse("https://api.weather.gov/icons/land/day/mystery", "Patchy Fog"),
        )
    }

    @Test
    fun `text parsing prefers the most specific phrase`() {
        assertEquals(
            WeatherCondition.THUNDERSTORM,
            WeatherConditionParser.fromShortForecast("Chance Showers And Thunderstorms"),
        )
        assertEquals(WeatherCondition.RAIN_SNOW, WeatherConditionParser.fromShortForecast("Rain And Snow Likely"))
        assertEquals(WeatherCondition.MOSTLY_CLOUDY, WeatherConditionParser.fromShortForecast("Partly Sunny"))
        assertEquals(WeatherCondition.CLEAR, WeatherConditionParser.fromShortForecast("Sunny"))
    }

    @Test
    fun `missing inputs yield unknown`() {
        assertNull(WeatherConditionParser.fromIconUrl(null))
        assertNull(WeatherConditionParser.fromIconUrl("not a url"))
        assertEquals(WeatherCondition.UNKNOWN, WeatherConditionParser.parse(null, ""))
    }
}

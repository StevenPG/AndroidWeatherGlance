package com.stevenpg.weatherglance.data.nws

import com.stevenpg.weatherglance.domain.model.WeatherCondition
import com.stevenpg.weatherglance.domain.model.WeatherCondition.BLIZZARD
import com.stevenpg.weatherglance.domain.model.WeatherCondition.CLEAR
import com.stevenpg.weatherglance.domain.model.WeatherCondition.COLD
import com.stevenpg.weatherglance.domain.model.WeatherCondition.DUST
import com.stevenpg.weatherglance.domain.model.WeatherCondition.FOG
import com.stevenpg.weatherglance.domain.model.WeatherCondition.FREEZING_RAIN
import com.stevenpg.weatherglance.domain.model.WeatherCondition.HAZE
import com.stevenpg.weatherglance.domain.model.WeatherCondition.HOT
import com.stevenpg.weatherglance.domain.model.WeatherCondition.HURRICANE
import com.stevenpg.weatherglance.domain.model.WeatherCondition.MOSTLY_CLEAR
import com.stevenpg.weatherglance.domain.model.WeatherCondition.MOSTLY_CLOUDY
import com.stevenpg.weatherglance.domain.model.WeatherCondition.OVERCAST
import com.stevenpg.weatherglance.domain.model.WeatherCondition.PARTLY_CLOUDY
import com.stevenpg.weatherglance.domain.model.WeatherCondition.RAIN
import com.stevenpg.weatherglance.domain.model.WeatherCondition.RAIN_SHOWERS
import com.stevenpg.weatherglance.domain.model.WeatherCondition.RAIN_SNOW
import com.stevenpg.weatherglance.domain.model.WeatherCondition.SLEET
import com.stevenpg.weatherglance.domain.model.WeatherCondition.SMOKE
import com.stevenpg.weatherglance.domain.model.WeatherCondition.SNOW
import com.stevenpg.weatherglance.domain.model.WeatherCondition.THUNDERSTORM
import com.stevenpg.weatherglance.domain.model.WeatherCondition.TORNADO
import com.stevenpg.weatherglance.domain.model.WeatherCondition.TROPICAL_STORM
import com.stevenpg.weatherglance.domain.model.WeatherCondition.UNKNOWN
import com.stevenpg.weatherglance.domain.model.WeatherCondition.WINDY

/**
 * Derives a [WeatherCondition] from an NWS period.
 *
 * The icon URL is the most reliable signal: it encodes condition codes such as
 * `https://api.weather.gov/icons/land/day/bkn/snow,30?size=medium` ("broken clouds, then snow 30%").
 * When a period has two codes, the more significant one wins. The free-text short forecast is
 * only a fallback for unrecognized or missing icons.
 */
object WeatherConditionParser {

    private val iconCodes: Map<String, WeatherCondition> = mapOf(
        "skc" to CLEAR,
        "few" to MOSTLY_CLEAR,
        "sct" to PARTLY_CLOUDY,
        "bkn" to MOSTLY_CLOUDY,
        "ovc" to OVERCAST,
        "wind_skc" to WINDY,
        "wind_few" to WINDY,
        "wind_sct" to WINDY,
        "wind_bkn" to WINDY,
        "wind_ovc" to WINDY,
        "snow" to SNOW,
        "rain_snow" to RAIN_SNOW,
        "rain_sleet" to SLEET,
        "snow_sleet" to SLEET,
        "sleet" to SLEET,
        "fzra" to FREEZING_RAIN,
        "rain_fzra" to FREEZING_RAIN,
        "snow_fzra" to FREEZING_RAIN,
        "rain" to RAIN,
        "rain_showers" to RAIN_SHOWERS,
        "rain_showers_hi" to RAIN_SHOWERS,
        "tsra" to THUNDERSTORM,
        "tsra_sct" to THUNDERSTORM,
        "tsra_hi" to THUNDERSTORM,
        "tornado" to TORNADO,
        "hurricane" to HURRICANE,
        "tropical_storm" to TROPICAL_STORM,
        "dust" to DUST,
        "smoke" to SMOKE,
        "haze" to HAZE,
        "hot" to HOT,
        "cold" to COLD,
        "blizzard" to BLIZZARD,
        "fog" to FOG,
    )

    // Ordered most-specific first; the first matching phrase wins.
    private val keywords: List<Pair<String, WeatherCondition>> = listOf(
        "tornado" to TORNADO,
        "hurricane" to HURRICANE,
        "tropical storm" to TROPICAL_STORM,
        "thunder" to THUNDERSTORM,
        "t-storm" to THUNDERSTORM,
        "blizzard" to BLIZZARD,
        "freezing" to FREEZING_RAIN,
        "sleet" to SLEET,
        "wintry mix" to SLEET,
        "rain and snow" to RAIN_SNOW,
        "snow and rain" to RAIN_SNOW,
        "snow" to SNOW,
        "flurries" to SNOW,
        "showers" to RAIN_SHOWERS,
        "drizzle" to RAIN,
        "rain" to RAIN,
        "fog" to FOG,
        "smoke" to SMOKE,
        "haze" to HAZE,
        "dust" to DUST,
        "windy" to WINDY,
        "breezy" to WINDY,
        "mostly cloudy" to MOSTLY_CLOUDY,
        "partly sunny" to MOSTLY_CLOUDY,
        "partly cloudy" to PARTLY_CLOUDY,
        "mostly sunny" to MOSTLY_CLEAR,
        "mostly clear" to MOSTLY_CLEAR,
        "cloudy" to OVERCAST,
        "overcast" to OVERCAST,
        "sunny" to CLEAR,
        "clear" to CLEAR,
        "hot" to HOT,
        "cold" to COLD,
    )

    fun parse(iconUrl: String?, shortForecast: String?): WeatherCondition =
        fromIconUrl(iconUrl) ?: fromShortForecast(shortForecast) ?: UNKNOWN

    fun fromIconUrl(iconUrl: String?): WeatherCondition? {
        if (iconUrl.isNullOrBlank()) return null
        val path = iconUrl.substringBefore('?')
        val marker = path.indexOf("/icons/")
        if (marker < 0) return null
        // /icons/{set}/{day|night}/{code[,pct]}[/{code[,pct]}]
        val segments = path.substring(marker + "/icons/".length).split('/').drop(2)
        return segments
            .mapNotNull { iconCodes[it.substringBefore(',')] }
            .maxOrNull()
    }

    fun fromShortForecast(shortForecast: String?): WeatherCondition? {
        if (shortForecast.isNullOrBlank()) return null
        val text = shortForecast.lowercase()
        return keywords.firstOrNull { (phrase, _) -> text.contains(phrase) }?.second
    }
}

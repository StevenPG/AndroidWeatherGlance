package com.stevenpg.weatherglance.domain.model

/**
 * Normalized weather conditions.
 *
 * Declaration order doubles as *significance*: when a forecast period describes more than one
 * condition (e.g. "Mostly Cloudy then Chance Snow"), the later entry wins because it is the one a
 * person most needs to know about.
 */
enum class WeatherCondition {
    UNKNOWN,
    CLEAR,
    MOSTLY_CLEAR,
    PARTLY_CLOUDY,
    MOSTLY_CLOUDY,
    OVERCAST,
    HAZE,
    SMOKE,
    DUST,
    FOG,
    WINDY,
    HOT,
    COLD,
    RAIN_SHOWERS,
    RAIN,
    RAIN_SNOW,
    SLEET,
    SNOW,
    FREEZING_RAIN,
    THUNDERSTORM,
    BLIZZARD,
    TROPICAL_STORM,
    HURRICANE,
    TORNADO,
}

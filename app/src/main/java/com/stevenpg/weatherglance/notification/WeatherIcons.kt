package com.stevenpg.weatherglance.notification

import androidx.annotation.DrawableRes
import com.stevenpg.weatherglance.R
import com.stevenpg.weatherglance.domain.model.WeatherCondition

object WeatherIcons {

    @DrawableRes
    @Suppress("CyclomaticComplexMethod")
    fun forCondition(condition: WeatherCondition, night: Boolean = false): Int = when (condition) {
        WeatherCondition.CLEAR -> if (night) R.drawable.ic_wx_clear_night else R.drawable.ic_wx_clear_day
        WeatherCondition.MOSTLY_CLEAR,
        WeatherCondition.PARTLY_CLOUDY,
        -> if (night) R.drawable.ic_wx_partly_cloudy_night else R.drawable.ic_wx_partly_cloudy_day
        WeatherCondition.MOSTLY_CLOUDY -> R.drawable.ic_wx_mostly_cloudy
        WeatherCondition.OVERCAST -> R.drawable.ic_wx_cloudy
        WeatherCondition.HAZE,
        WeatherCondition.SMOKE,
        WeatherCondition.DUST,
        -> R.drawable.ic_wx_haze
        WeatherCondition.FOG -> R.drawable.ic_wx_fog
        WeatherCondition.WINDY -> R.drawable.ic_wx_wind
        WeatherCondition.HOT -> R.drawable.ic_wx_hot
        WeatherCondition.COLD -> R.drawable.ic_wx_cold
        WeatherCondition.RAIN_SHOWERS -> R.drawable.ic_wx_showers
        WeatherCondition.RAIN -> R.drawable.ic_wx_rain
        WeatherCondition.RAIN_SNOW,
        WeatherCondition.SLEET,
        -> R.drawable.ic_wx_sleet
        WeatherCondition.FREEZING_RAIN -> R.drawable.ic_wx_freezing_rain
        WeatherCondition.SNOW,
        WeatherCondition.BLIZZARD,
        -> R.drawable.ic_wx_snow
        WeatherCondition.THUNDERSTORM -> R.drawable.ic_wx_thunderstorm
        WeatherCondition.TROPICAL_STORM,
        WeatherCondition.HURRICANE,
        -> R.drawable.ic_wx_hurricane
        WeatherCondition.TORNADO -> R.drawable.ic_wx_tornado
        WeatherCondition.UNKNOWN -> R.drawable.ic_wx_cloudy
    }
}

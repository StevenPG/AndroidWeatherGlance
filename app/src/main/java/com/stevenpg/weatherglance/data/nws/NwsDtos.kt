package com.stevenpg.weatherglance.data.nws

import kotlinx.serialization.Serializable

// Minimal projections of the api.weather.gov GeoJSON payloads. Unknown keys are ignored.

@Serializable
data class PointsResponse(val properties: PointsProperties)

@Serializable
data class PointsProperties(
    val forecast: String? = null,
    val timeZone: String? = null,
    val relativeLocation: RelativeLocation? = null,
)

@Serializable
data class RelativeLocation(val properties: RelativeLocationProperties? = null)

@Serializable
data class RelativeLocationProperties(val city: String? = null, val state: String? = null)

@Serializable
data class ForecastResponse(val properties: ForecastProperties)

@Serializable
data class ForecastProperties(val periods: List<ForecastPeriod> = emptyList())

@Serializable
data class ForecastPeriod(
    val name: String = "",
    val startTime: String,
    val isDaytime: Boolean,
    val temperature: Double? = null,
    val probabilityOfPrecipitation: QuantitativeValue? = null,
    val icon: String? = null,
    val shortForecast: String = "",
    val detailedForecast: String = "",
)

@Serializable
data class QuantitativeValue(val value: Double? = null)

@Serializable
data class AlertsResponse(val features: List<AlertFeature> = emptyList())

@Serializable
data class AlertFeature(val properties: AlertProperties)

@Serializable
data class AlertProperties(
    val id: String,
    val event: String = "",
    val headline: String? = null,
    val severity: String? = null,
    val status: String? = null,
    val expires: String? = null,
    val ends: String? = null,
    val description: String? = null,
    val instruction: String? = null,
)

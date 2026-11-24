package com.stevenpg.weatherglance.data.nws

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import java.math.BigDecimal
import java.math.RoundingMode

interface NwsApi {
    @GET("points/{point}")
    suspend fun points(@Path("point", encoded = true) point: String): PointsResponse

    @GET
    suspend fun forecast(@Url forecastUrl: String): ForecastResponse

    @GET("alerts/active")
    suspend fun activeAlerts(@Query("point", encoded = true) point: String): AlertsResponse

    companion object {
        const val BASE_URL = "https://api.weather.gov/"
    }
}

object NwsPoint {
    /** NWS redirects requests with more than four decimal places, so normalize up front. */
    private const val MAX_DECIMALS = 4

    fun format(latitude: Double, longitude: Double): String = "${format(latitude)},${format(longitude)}"

    private fun format(value: Double): String =
        BigDecimal.valueOf(value).setScale(MAX_DECIMALS, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}

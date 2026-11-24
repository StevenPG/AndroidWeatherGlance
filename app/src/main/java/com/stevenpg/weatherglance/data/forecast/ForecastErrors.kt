package com.stevenpg.weatherglance.data.forecast

import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/** The NWS has no forecast for these coordinates (it only covers the US and its territories). */
class UnsupportedLocationException : Exception("NWS does not provide forecasts for this location")

/** NWS answered successfully but without usable forecast periods. Usually transient. */
class EmptyForecastException : IOException("NWS returned no forecast periods")

private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_REQUEST_TIMEOUT = 408
private const val HTTP_SERVER_ERROR = 500

/** Whether retrying the same request later has a reasonable chance of succeeding. */
fun Throwable.isTransient(): Boolean = when (this) {
    is UnsupportedLocationException -> false
    is SerializationException -> false
    is IOException -> true
    is HttpException -> code() >= HTTP_SERVER_ERROR || code() == HTTP_TOO_MANY_REQUESTS ||
        code() == HTTP_REQUEST_TIMEOUT
    else -> false
}

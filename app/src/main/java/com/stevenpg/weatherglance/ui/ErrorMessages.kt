package com.stevenpg.weatherglance.ui

import androidx.annotation.StringRes
import com.stevenpg.weatherglance.R
import com.stevenpg.weatherglance.data.forecast.UnsupportedLocationException
import com.stevenpg.weatherglance.data.location.GeocoderUnavailableException
import retrofit2.HttpException
import java.io.IOException

@StringRes
fun Throwable.toMessageRes(): Int = when (this) {
    is UnsupportedLocationException -> R.string.error_unsupported_location
    is GeocoderUnavailableException -> R.string.error_geocoder_unavailable
    is HttpException -> R.string.error_nws
    is IOException -> R.string.error_network
    else -> R.string.error_generic
}

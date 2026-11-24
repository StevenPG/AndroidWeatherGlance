package com.stevenpg.weatherglance.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PlaceResult(
    val name: String,
    val detail: String,
    val latitude: Double,
    val longitude: Double,
)

class GeocoderUnavailableException : Exception("No geocoder is available on this device")

/** City / ZIP search via the platform [Geocoder]. Results are limited to NWS coverage areas. */
@Singleton
class LocationSearchService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun search(query: String): List<PlaceResult> {
        if (!Geocoder.isPresent()) throw GeocoderUnavailableException()
        val geocoder = Geocoder(context, Locale.US)
        val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocationName(
                    query,
                    MAX_RESULTS,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = continuation.resume(addresses)

                        override fun onError(errorMessage: String?) =
                            continuation.resumeWithException(IOException(errorMessage ?: "Geocoding failed"))
                    },
                )
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query, MAX_RESULTS).orEmpty()
            }
        }
        return addresses
            .filter { it.hasLatitude() && it.hasLongitude() && it.countryCode in NWS_COUNTRY_CODES }
            .map { it.toPlaceResult() }
            .distinctBy { it.name to it.detail }
    }

    private fun Address.toPlaceResult(): PlaceResult {
        val place = locality ?: subLocality ?: subAdminArea ?: featureName ?: adminArea.orEmpty()
        val region = UsStates.abbreviate(adminArea)
        return PlaceResult(
            name = listOfNotNull(place, region).filter { it.isNotBlank() }.distinct().joinToString(", "),
            detail = listOfNotNull(postalCode, subAdminArea, adminArea).distinct().joinToString(" · "),
            latitude = latitude,
            longitude = longitude,
        )
    }

    private companion object {
        const val MAX_RESULTS = 8

        /** NWS covers the US and its territories. */
        val NWS_COUNTRY_CODES = setOf("US", "PR", "GU", "VI", "AS", "MP")
    }
}

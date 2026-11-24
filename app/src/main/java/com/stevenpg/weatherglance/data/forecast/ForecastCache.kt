package com.stevenpg.weatherglance.data.forecast

import com.stevenpg.weatherglance.domain.model.ForecastSnapshot
import kotlinx.serialization.Serializable

@Serializable
data class ForecastCache(
    val snapshot: ForecastSnapshot? = null,
    /** Alert IDs that already produced a notification, so each alert is announced once. */
    val notifiedAlertIds: Set<String> = emptySet(),
)

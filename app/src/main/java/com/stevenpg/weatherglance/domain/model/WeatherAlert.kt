package com.stevenpg.weatherglance.domain.model

import kotlinx.serialization.Serializable
import java.time.Instant

/** CAP severity levels. Declaration order is ascending severity so enums compare naturally. */
enum class AlertSeverity {
    UNKNOWN,
    MINOR,
    MODERATE,
    SEVERE,
    EXTREME,
    ;

    companion object {
        fun fromNws(value: String?): AlertSeverity =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

@Serializable
data class WeatherAlert(
    val id: String,
    val event: String,
    val headline: String?,
    val severity: AlertSeverity,
    @Serializable(with = InstantEpochMillisSerializer::class)
    val endsAt: Instant?,
    val description: String?,
    val instruction: String?,
)

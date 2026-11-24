package com.stevenpg.weatherglance.data.forecast

import androidx.datastore.core.DataStore
import com.stevenpg.weatherglance.data.nws.ForecastPeriod
import com.stevenpg.weatherglance.data.nws.NwsApi
import com.stevenpg.weatherglance.data.nws.NwsMapper
import com.stevenpg.weatherglance.data.nws.NwsPoint
import com.stevenpg.weatherglance.data.settings.SettingsRepository
import com.stevenpg.weatherglance.domain.WidgetUpdater
import com.stevenpg.weatherglance.domain.model.ForecastSnapshot
import com.stevenpg.weatherglance.domain.model.SavedLocation
import com.stevenpg.weatherglance.domain.model.WeatherAlert
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForecastRepository @Inject constructor(
    private val api: NwsApi,
    private val cacheStore: DataStore<ForecastCache>,
    private val settingsRepository: SettingsRepository,
    private val widgetUpdater: WidgetUpdater,
    private val clock: Clock,
) {
    val snapshot: Flow<ForecastSnapshot?> = cacheStore.data.map { it.snapshot }

    suspend fun cachedSnapshot(): ForecastSnapshot? = snapshot.first()

    /** Cached snapshot for [location] that is no older than [maxAge], if any. */
    suspend fun usableCachedSnapshot(location: SavedLocation, maxAge: Duration): ForecastSnapshot? =
        cachedSnapshot()?.takeIf { cached ->
            cached.location.hasSameCoordinates(location) &&
                Duration.between(cached.fetchedAt, Instant.now(clock)) <= maxAge
        }

    /**
     * Resolves coordinates to an NWS forecast grid. Throws [UnsupportedLocationException] for
     * coordinates outside NWS coverage.
     */
    suspend fun resolveLocation(latitude: Double, longitude: Double, displayName: String?): SavedLocation {
        val properties = try {
            api.points(NwsPoint.format(latitude, longitude)).properties
        } catch (e: HttpException) {
            throw if (e.code() == HTTP_NOT_FOUND) UnsupportedLocationException() else e
        }
        val forecastUrl = properties.forecast ?: throw UnsupportedLocationException()
        val relative = properties.relativeLocation?.properties
        val name = displayName?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(relative?.city, relative?.state).joinToString(", ").ifBlank { null }
            ?: NwsPoint.format(latitude, longitude)
        return SavedLocation(
            displayName = name,
            latitude = latitude,
            longitude = longitude,
            forecastUrl = forecastUrl,
            timeZoneId = properties.timeZone ?: ZoneId.systemDefault().id,
        )
    }

    /** Fetches a fresh forecast (and, best-effort, active alerts) and stores it as the cached snapshot. */
    suspend fun refresh(location: SavedLocation): ForecastSnapshot = coroutineScope {
        val alertsRequest = async { fetchAlertsOrNull(location) }
        val (resolvedLocation, periods) = fetchPeriods(location)
        val days = NwsMapper.toDailyForecasts(periods)
        if (days.isEmpty()) throw EmptyForecastException()

        val alerts = alertsRequest.await()
            ?: usableCachedSnapshot(location, Duration.ofHours(STALE_ALERTS_HOURS))?.alerts
            ?: emptyList()
        val snapshot = ForecastSnapshot(
            location = resolvedLocation,
            fetchedAt = Instant.now(clock),
            days = days,
            alerts = alerts,
        )
        cacheStore.updateData { it.copy(snapshot = snapshot) }
        widgetUpdater.requestUpdate()
        snapshot
    }

    /** Refreshes only the alerts on the cached snapshot. Returns the active alerts. */
    suspend fun refreshAlerts(location: SavedLocation): List<WeatherAlert> {
        val alerts = NwsMapper.toAlerts(
            api.activeAlerts(NwsPoint.format(location.latitude, location.longitude)),
            Instant.now(clock),
        )
        cacheStore.updateData { cache ->
            val current = cache.snapshot
            if (current != null && current.location.hasSameCoordinates(location)) {
                cache.copy(snapshot = current.copy(alerts = alerts))
            } else {
                cache
            }
        }
        widgetUpdater.requestUpdate()
        return alerts
    }

    suspend fun notifiedAlertIds(): Set<String> = cacheStore.data.first().notifiedAlertIds

    suspend fun setNotifiedAlertIds(ids: Set<String>) {
        cacheStore.updateData { it.copy(notifiedAlertIds = ids) }
    }

    suspend fun clearSnapshot() {
        cacheStore.updateData { ForecastCache() }
        widgetUpdater.requestUpdate()
    }

    private suspend fun fetchPeriods(location: SavedLocation): Pair<SavedLocation, List<ForecastPeriod>> = try {
        location to api.forecast(location.forecastUrl).properties.periods
    } catch (e: HttpException) {
        if (e.code() != HTTP_NOT_FOUND) throw e
        // NWS occasionally re-grids an office; resolve the point again and retry once.
        val updated = resolveLocation(location.latitude, location.longitude, location.displayName)
        settingsRepository.update { settings ->
            if (location.hasSameCoordinates(settings.location)) settings.copy(location = updated) else settings
        }
        updated to api.forecast(updated.forecastUrl).properties.periods
    }

    private suspend fun fetchAlertsOrNull(location: SavedLocation): List<WeatherAlert>? = try {
        NwsMapper.toAlerts(
            api.activeAlerts(NwsPoint.format(location.latitude, location.longitude)),
            Instant.now(clock),
        )
    } catch (_: IOException) {
        null
    } catch (_: HttpException) {
        null
    } catch (_: SerializationException) {
        null
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
        const val STALE_ALERTS_HOURS = 6L
    }
}

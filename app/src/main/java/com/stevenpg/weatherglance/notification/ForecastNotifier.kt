package com.stevenpg.weatherglance.notification

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.stevenpg.weatherglance.data.forecast.ForecastRepository
import com.stevenpg.weatherglance.data.forecast.isTransient
import com.stevenpg.weatherglance.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** Fetch → (fallback to cache) → post. Shared by scheduled work, the Refresh action and "Notify now". */
@Singleton
class ForecastNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val forecastRepository: ForecastRepository,
    private val notificationFactory: ForecastNotificationFactory,
    private val alertNotifier: AlertNotifier,
) {
    sealed interface Outcome {
        data class Posted(val fromCache: Boolean) : Outcome
        data object NoLocation : Outcome
        data object NotificationsBlocked : Outcome
        data class Failed(val error: Throwable, val retryable: Boolean) : Outcome
    }

    @SuppressLint("MissingPermission") // Guarded by areNotificationsEnabled().
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    suspend fun publish(useCacheOnFailure: Boolean): Outcome {
        val settings = settingsRepository.current()
        val location = settings.location ?: return Outcome.NoLocation

        var fromCache = false
        val snapshot = try {
            forecastRepository.refresh(location)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val cached = forecastRepository.usableCachedSnapshot(location, MAX_CACHE_AGE)
                .takeIf { useCacheOnFailure }
            cached ?: return Outcome.Failed(e, retryable = e.isTransient())
            fromCache = true
            cached
        }

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return Outcome.NotificationsBlocked

        val notification = notificationFactory.build(
            snapshot = snapshot,
            unit = settings.temperatureUnit,
            fromCache = fromCache,
            visibleAlertSeverity = if (settings.alertsEnabled) settings.minAlertSeverity else null,
        ) ?: return Outcome.Failed(IllegalStateException("No forecast days remain"), retryable = false)

        manager.notify(ForecastNotificationFactory.NOTIFICATION_ID, notification)
        if (!fromCache && settings.alertsEnabled) {
            alertNotifier.notifyNew(snapshot.location, snapshot.alerts, settings.minAlertSeverity)
        }
        return Outcome.Posted(fromCache)
    }

    private companion object {
        val MAX_CACHE_AGE: Duration = Duration.ofHours(24)
    }
}

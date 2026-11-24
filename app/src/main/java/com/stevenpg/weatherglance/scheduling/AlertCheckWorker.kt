package com.stevenpg.weatherglance.scheduling

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.stevenpg.weatherglance.data.forecast.ForecastRepository
import com.stevenpg.weatherglance.data.forecast.isTransient
import com.stevenpg.weatherglance.data.settings.SettingsRepository
import com.stevenpg.weatherglance.notification.AlertNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/** Polls NWS active alerts between scheduled forecasts so severe weather isn't missed. */
@HiltWorker
class AlertCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val forecastRepository: ForecastRepository,
    private val alertNotifier: AlertNotifier,
) : CoroutineWorker(context, params) {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        val settings = settingsRepository.current()
        val location = settings.location
        if (!settings.alertsEnabled || location == null) return Result.success()
        return try {
            val alerts = forecastRepository.refreshAlerts(location)
            alertNotifier.notifyNew(location, alerts, settings.minAlertSeverity)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Periodic work runs again within the hour anyway; only retry quick transient blips.
            if (e.isTransient() && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        }
    }

    private companion object {
        const val MAX_RETRIES = 2
    }
}

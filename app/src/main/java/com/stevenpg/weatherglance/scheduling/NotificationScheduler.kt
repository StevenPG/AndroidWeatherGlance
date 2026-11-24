package com.stevenpg.weatherglance.scheduling

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.stevenpg.weatherglance.data.settings.AppSettings
import com.stevenpg.weatherglance.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules forecast notifications with WorkManager.
 *
 * Each upcoming slot is one [ForecastNotificationWorker] enqueued as unique work named after its
 * target instant, with [ExistingWorkPolicy.KEEP]. That makes scheduling idempotent: the worker
 * (on every attempt, including retries), app launch, and system time-change broadcasts can all ask
 * for "the next slot" without ever producing duplicates. Changing the schedule cancels everything
 * under [TAG_SCHEDULED] and enqueues the new next slot.
 *
 * Slots are intentionally not network-constrained: a constrained job would sit idle while offline,
 * defeating the "fall back to the cached forecast" behavior.
 */
@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /** Cancels any pending slot and schedules from the current settings. Call after settings change. */
    suspend fun reschedule() {
        workManager.cancelAllWorkByTag(TAG_SCHEDULED)
        scheduleNext(after = Instant.now(clock))
        syncAlertChecks()
    }

    /** Ensures the next slot exists without disturbing work already queued or running. */
    suspend fun ensureScheduled() {
        scheduleNext(after = Instant.now(clock))
        syncAlertChecks()
    }

    /** Next slot time in the device's zone, or null when notifications are off or unconfigured. */
    suspend fun nextSlot(): ZonedDateTime? = nextSlot(settingsRepository.current(), Instant.now(clock))

    /** Enqueues the first slot strictly after [after]. Safe to call repeatedly. */
    suspend fun scheduleNext(after: Instant) {
        val settings = settingsRepository.current()
        val now = Instant.now(clock)
        val next = nextSlot(settings, maxOf(after, now)) ?: return
        val delay = Duration.between(now, next.toInstant()).coerceAtLeast(Duration.ZERO)

        val request = OneTimeWorkRequestBuilder<ForecastNotificationWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setInputData(
                workDataOf(
                    ForecastNotificationWorker.KEY_MODE to ForecastNotificationWorker.MODE_SCHEDULED,
                    ForecastNotificationWorker.KEY_TARGET_EPOCH_MILLIS to next.toInstant().toEpochMilli(),
                ),
            )
            .addTag(TAG_SCHEDULED)
            .build()

        workManager.enqueueUniqueWork(
            "$SLOT_WORK_PREFIX${next.toEpochSecond()}",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /** Fetch-and-notify outside the schedule (the notification's "Refresh" action). */
    fun enqueueRefreshNow() {
        val request = OneTimeWorkRequestBuilder<ForecastNotificationWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setInputData(workDataOf(ForecastNotificationWorker.KEY_MODE to ForecastNotificationWorker.MODE_MANUAL))
            .build()
        workManager.enqueueUniqueWork(MANUAL_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** Hourly alert polling between forecasts, when alerts are on and a location is set. */
    suspend fun syncAlertChecks() {
        val settings = settingsRepository.current()
        if (!settings.alertsEnabled || settings.location == null) {
            workManager.cancelUniqueWork(ALERT_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AlertCheckWorker>(
            ALERT_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
            ALERT_FLEX_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(ALERT_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun nextSlot(settings: AppSettings, after: Instant): ZonedDateTime? {
        if (!settings.notificationsEnabled || settings.location == null) return null
        return ScheduleCalculator.nextOccurrence(
            times = settings.notificationTimes,
            activeDays = settings.activeDaysOfWeek,
            after = after.atZone(ZoneId.systemDefault()),
        )
    }

    companion object {
        const val TAG_SCHEDULED = "forecast-scheduled"
        private const val SLOT_WORK_PREFIX = "forecast-slot-"
        private const val MANUAL_WORK_NAME = "forecast-manual"
        private const val ALERT_WORK_NAME = "alert-check"
        private const val BACKOFF_SECONDS = 30L
        private const val ALERT_INTERVAL_MINUTES = 60L
        private const val ALERT_FLEX_MINUTES = 15L
    }
}

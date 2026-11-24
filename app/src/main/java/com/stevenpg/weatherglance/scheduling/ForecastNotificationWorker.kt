package com.stevenpg.weatherglance.scheduling

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.stevenpg.weatherglance.notification.ForecastNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.Duration
import java.time.Instant

@HiltWorker
class ForecastNotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifier: ForecastNotifier,
    private val scheduler: NotificationScheduler,
    private val clock: Clock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val scheduled = inputData.getString(KEY_MODE) != MODE_MANUAL
        val now = Instant.now(clock)

        if (scheduled) {
            val target = Instant.ofEpochMilli(inputData.getLong(KEY_TARGET_EPOCH_MILLIS, now.toEpochMilli()))
            // Chain the next slot first so a failure here can never break the schedule. Idempotent on retry.
            scheduler.scheduleNext(after = target)
            if (Duration.between(target, now) > MAX_LATENESS) {
                // e.g. the device was off at 7:00 and booted at noon: a "morning" forecast is no longer useful.
                return Result.success()
            }
        }

        val finalAttempt = runAttemptCount >= MAX_RETRIES
        return when (val outcome = notifier.publish(useCacheOnFailure = finalAttempt)) {
            is ForecastNotifier.Outcome.Posted,
            ForecastNotifier.Outcome.NoLocation,
            ForecastNotifier.Outcome.NotificationsBlocked,
            -> Result.success()

            is ForecastNotifier.Outcome.Failed ->
                if (outcome.retryable && !finalAttempt) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_MODE = "mode"
        const val KEY_TARGET_EPOCH_MILLIS = "target"
        const val MODE_SCHEDULED = "scheduled"
        const val MODE_MANUAL = "manual"

        /** Attempts 0..3 → retries after ~30s, 60s, 120s, then fall back to the cache. */
        private const val MAX_RETRIES = 3
        private val MAX_LATENESS: Duration = Duration.ofHours(2)
    }
}

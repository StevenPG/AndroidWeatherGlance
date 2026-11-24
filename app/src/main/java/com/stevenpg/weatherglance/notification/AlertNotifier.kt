package com.stevenpg.weatherglance.notification

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.stevenpg.weatherglance.R
import com.stevenpg.weatherglance.data.forecast.ForecastRepository
import com.stevenpg.weatherglance.domain.model.AlertSeverity
import com.stevenpg.weatherglance.domain.model.SavedLocation
import com.stevenpg.weatherglance.domain.model.WeatherAlert
import com.stevenpg.weatherglance.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Posts one high-priority notification per new NWS alert at or above the configured severity. */
@Singleton
class AlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val forecastRepository: ForecastRepository,
    private val clock: Clock,
) {
    @SuppressLint("MissingPermission") // Guarded by areNotificationsEnabled().
    suspend fun notifyNew(location: SavedLocation, alerts: List<WeatherAlert>, minSeverity: AlertSeverity) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val alreadyNotified = forecastRepository.notifiedAlertIds()
        val fresh = alerts.filter { it.severity >= minSeverity && it.id !in alreadyNotified }
        fresh.forEach { alert -> manager.notify(alert.id, ALERT_NOTIFICATION_ID, build(location, alert)) }

        // Keep only IDs that are still active so the set stays bounded.
        val activeIds = alerts.map { it.id }.toSet()
        forecastRepository.setNotifiedAlertIds((alreadyNotified intersect activeIds) + fresh.map { it.id })
    }

    private fun build(location: SavedLocation, alert: WeatherAlert) =
        NotificationCompat.Builder(context, NotificationChannels.ALERTS)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setColor(ContextCompat.getColor(context, R.color.alert))
            .setContentTitle(alert.event)
            .setContentText(alert.headline ?: alert.event)
            .setSubText(location.displayName)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText(alert)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroup(GROUP_ALERTS)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    alert.id.hashCode(),
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .apply {
                alert.endsAt
                    ?.let { Duration.between(Instant.now(clock), it) }
                    ?.takeIf { !it.isNegative }
                    ?.let { setTimeoutAfter(it.toMillis()) }
            }
            .build()

    private fun bigText(alert: WeatherAlert): String = listOfNotNull(
        alert.headline,
        alert.description?.take(MAX_DESCRIPTION_CHARS),
        alert.instruction?.take(MAX_DESCRIPTION_CHARS),
    ).joinToString("\n\n")

    private companion object {
        const val ALERT_NOTIFICATION_ID = 2001
        const val GROUP_ALERTS = "weather_alerts"
        const val MAX_DESCRIPTION_CHARS = 1200
    }
}

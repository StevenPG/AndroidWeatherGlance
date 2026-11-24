package com.stevenpg.weatherglance.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.stevenpg.weatherglance.R
import com.stevenpg.weatherglance.domain.model.AlertSeverity
import com.stevenpg.weatherglance.domain.model.DailyForecast
import com.stevenpg.weatherglance.domain.model.ForecastSnapshot
import com.stevenpg.weatherglance.domain.model.TemperatureUnit
import com.stevenpg.weatherglance.domain.model.WeatherAlert
import com.stevenpg.weatherglance.ui.MainActivity
import com.stevenpg.weatherglance.ui.format.ForecastFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the forecast notification: three days in the collapsed view, five when expanded.
 *
 * Uses [NotificationCompat.DecoratedCustomViewStyle] so the system still draws the standard header,
 * timestamp and actions, and the custom content picks up the platform's light/dark text colors.
 */
@Singleton
class ForecastNotificationFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    fun build(
        snapshot: ForecastSnapshot,
        unit: TemperatureUnit,
        fromCache: Boolean,
        visibleAlertSeverity: AlertSeverity?,
    ): Notification? {
        val zone = snapshot.location.zoneId()
        val today = LocalDate.now(clock.withZone(zone))
        val days = snapshot.daysFrom(today)
        if (days.isEmpty()) return null

        val formatter = ForecastFormatter(context, unit, today)
        val alert = visibleAlertSeverity?.let { min -> snapshot.alerts.firstOrNull { it.severity >= min } }
        val staleNote = if (fromCache) context.getString(R.string.notification_saved_forecast) else null

        return NotificationCompat.Builder(context, NotificationChannels.FORECAST)
            .setSmallIcon(R.drawable.ic_stat_weather)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle(snapshot.location.displayName)
            .setContentText(formatter.summary(days.first()))
            .setSubText(staleNote)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsedView(days.take(COLLAPSED_DAYS), formatter))
            .setCustomBigContentView(expandedView(days.take(EXPANDED_DAYS), formatter, alert, zone))
            .setWhen(snapshot.fetchedAt.toEpochMilli())
            .setShowWhen(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .addAction(R.drawable.ic_refresh, context.getString(R.string.action_refresh), refreshIntent())
            .build()
    }

    private fun collapsedView(days: List<DailyForecast>, formatter: ForecastFormatter): RemoteViews =
        RemoteViews(context.packageName, R.layout.notification_forecast_collapsed).apply {
            removeAllViews(R.id.days)
            days.forEach { day ->
                addView(R.id.days, dayCell(R.layout.notification_day_compact, day, formatter))
            }
        }

    private fun expandedView(
        days: List<DailyForecast>,
        formatter: ForecastFormatter,
        alert: WeatherAlert?,
        zone: ZoneId,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.notification_forecast_expanded).apply {
        removeAllViews(R.id.days)
        days.forEach { day -> addView(R.id.days, dayCell(R.layout.notification_day_full, day, formatter)) }

        setTextViewText(R.id.summary, formatter.summary(days.first()))

        if (alert != null) {
            val until = alert.endsAt?.let {
                context.getString(R.string.alert_until, formatter.dayTime(it, zone))
            }.orEmpty()
            setTextViewText(R.id.alert, context.getString(R.string.alert_banner, alert.event, until).trim())
            setViewVisibility(R.id.alert, View.VISIBLE)
        } else {
            setViewVisibility(R.id.alert, View.GONE)
        }
    }

    private fun dayCell(layout: Int, day: DailyForecast, formatter: ForecastFormatter): RemoteViews =
        RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.day_label, formatter.dayLabel(day))
            setImageViewResource(R.id.day_icon, WeatherIcons.forCondition(day.condition, night = day.isNightOnly))
            setTextViewText(R.id.day_high, formatter.temperature(day.highF))
            setTextViewText(R.id.day_low, formatter.temperature(day.lowF))
            val precip = formatter.precip(day)
            setTextViewText(R.id.day_precip, precip.orEmpty())
            // INVISIBLE (not GONE) keeps columns vertically aligned when only some days have precip.
            setViewVisibility(R.id.day_precip, if (precip == null) View.INVISIBLE else View.VISIBLE)
            setContentDescription(R.id.day_root, formatter.contentDescription(day))
        }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        Intent(
            context,
            MainActivity::class.java,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun refreshIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_REFRESH,
        Intent(context, NotificationActionReceiver::class.java).setAction(NotificationActionReceiver.ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val NOTIFICATION_ID = 1001
        private const val COLLAPSED_DAYS = 3
        private const val EXPANDED_DAYS = 5
        private const val REQUEST_OPEN = 1
        private const val REQUEST_REFRESH = 2
    }
}

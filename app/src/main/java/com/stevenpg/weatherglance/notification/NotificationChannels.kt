package com.stevenpg.weatherglance.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import com.stevenpg.weatherglance.R

object NotificationChannels {
    const val FORECAST = "forecast"
    const val ALERTS = "severe_alerts"

    fun register(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    FORECAST,
                    context.getString(R.string.channel_forecast_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.channel_forecast_description)
                    setShowBadge(false)
                },
                NotificationChannel(
                    ALERTS,
                    context.getString(R.string.channel_alerts_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.channel_alerts_description)
                },
            ),
        )
    }
}

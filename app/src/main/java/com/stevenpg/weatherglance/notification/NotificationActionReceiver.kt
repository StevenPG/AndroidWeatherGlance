package com.stevenpg.weatherglance.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stevenpg.weatherglance.scheduling.NotificationScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduler: NotificationScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) scheduler.enqueueRefreshNow()
    }

    companion object {
        const val ACTION_REFRESH = "com.stevenpg.weatherglance.action.REFRESH"
    }
}

package com.stevenpg.weatherglance.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stevenpg.weatherglance.data.settings.AppSettings
import com.stevenpg.weatherglance.data.settings.NotificationTime
import com.stevenpg.weatherglance.data.settings.SettingsRepository
import com.stevenpg.weatherglance.domain.WidgetUpdater
import com.stevenpg.weatherglance.domain.model.AlertSeverity
import com.stevenpg.weatherglance.domain.model.TemperatureUnit
import com.stevenpg.weatherglance.scheduling.NotificationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scheduler: NotificationScheduler,
    private val widgetUpdater: WidgetUpdater,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun setNotificationsEnabled(enabled: Boolean) = updateSchedule { it.copy(notificationsEnabled = enabled) }

    fun addTime(hour: Int, minute: Int) = updateSchedule { settings ->
        val time = NotificationTime(hour, minute)
        if (time in settings.notificationTimes ||
            settings.notificationTimes.size >= AppSettings.MAX_NOTIFICATION_TIMES
        ) {
            settings
        } else {
            settings.copy(notificationTimes = (settings.notificationTimes + time).sorted())
        }
    }

    fun removeTime(time: NotificationTime) = updateSchedule { it.copy(notificationTimes = it.notificationTimes - time) }

    fun toggleDay(day: DayOfWeek) = updateSchedule { settings ->
        val days = settings.activeDays.let { if (day.value in it) it - day.value else it + day.value }
        // Keep at least one day; turning everything off is what the master switch is for.
        if (days.isEmpty()) settings else settings.copy(activeDays = days)
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(temperatureUnit = unit) }
            widgetUpdater.requestUpdate()
        }
    }

    fun setAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(alertsEnabled = enabled) }
            scheduler.syncAlertChecks()
        }
    }

    fun setMinAlertSeverity(severity: AlertSeverity) {
        viewModelScope.launch { settingsRepository.update { it.copy(minAlertSeverity = severity) } }
    }

    private fun updateSchedule(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val before = settingsRepository.current()
            val after = settingsRepository.update(transform)
            if (before != after) scheduler.reschedule()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

package com.stevenpg.weatherglance.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stevenpg.weatherglance.R
import com.stevenpg.weatherglance.data.forecast.ForecastRepository
import com.stevenpg.weatherglance.data.settings.SettingsRepository
import com.stevenpg.weatherglance.domain.model.AlertSeverity
import com.stevenpg.weatherglance.domain.model.ForecastSnapshot
import com.stevenpg.weatherglance.domain.model.SavedLocation
import com.stevenpg.weatherglance.domain.model.TemperatureUnit
import com.stevenpg.weatherglance.domain.model.WeatherAlert
import com.stevenpg.weatherglance.notification.ForecastNotifier
import com.stevenpg.weatherglance.scheduling.NotificationScheduler
import com.stevenpg.weatherglance.scheduling.ScheduleCalculator
import com.stevenpg.weatherglance.ui.toMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val location: SavedLocation? = null,
    val snapshot: ForecastSnapshot? = null,
    val unit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    val alerts: List<WeatherAlert> = emptyList(),
    val notificationsEnabled: Boolean = true,
    val nextNotification: ZonedDateTime? = null,
    val isRefreshing: Boolean = false,
    val isNotifying: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val forecastRepository: ForecastRepository,
    private val notifier: ForecastNotifier,
    private val scheduler: NotificationScheduler,
    private val clock: Clock,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val notifying = MutableStateFlow(false)
    private val messageChannel = Channel<Int>(Channel.BUFFERED)

    /** One-shot user-facing messages (string resource IDs) for the snackbar. */
    val messages: Flow<Int> = messageChannel.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        settingsRepository.settings,
        forecastRepository.snapshot,
        refreshing,
        notifying,
    ) { settings, snapshot, isRefreshing, isNotifying ->
        val current = snapshot?.takeIf { it.location.hasSameCoordinates(settings.location) }
        val minSeverity = if (settings.alertsEnabled) settings.minAlertSeverity else AlertSeverity.MODERATE
        HomeUiState(
            loading = false,
            location = settings.location,
            snapshot = current,
            unit = settings.temperatureUnit,
            alerts = current?.alerts.orEmpty().filter { it.severity >= minSeverity },
            notificationsEnabled = settings.notificationsEnabled,
            nextNotification = if (settings.notificationsEnabled && settings.location != null) {
                ScheduleCalculator.nextOccurrence(
                    settings.notificationTimes,
                    settings.activeDaysOfWeek,
                    Instant.now(clock).atZone(ZoneId.systemDefault()),
                )
            } else {
                null
            },
            isRefreshing = isRefreshing,
            isNotifying = isNotifying,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

    init {
        viewModelScope.launch {
            scheduler.ensureScheduled()
            val location = settingsRepository.current().location ?: return@launch
            if (forecastRepository.usableCachedSnapshot(location, AUTO_REFRESH_AFTER) == null) refresh()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            val location = settingsRepository.current().location ?: return@launch
            refreshing.value = true
            try {
                forecastRepository.refresh(location)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messageChannel.send(e.toMessageRes())
            } finally {
                refreshing.value = false
            }
        }
    }

    fun notifyNow() {
        if (notifying.value) return
        viewModelScope.launch {
            notifying.value = true
            try {
                val message = when (val outcome = notifier.publish(useCacheOnFailure = true)) {
                    is ForecastNotifier.Outcome.Posted ->
                        if (outcome.fromCache) R.string.message_sent_cached else R.string.message_sent
                    ForecastNotifier.Outcome.NoLocation -> R.string.message_choose_location
                    ForecastNotifier.Outcome.NotificationsBlocked -> R.string.message_notifications_blocked
                    is ForecastNotifier.Outcome.Failed -> outcome.error.toMessageRes()
                }
                messageChannel.send(message)
            } finally {
                notifying.value = false
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        val AUTO_REFRESH_AFTER: Duration = Duration.ofMinutes(30)
    }
}

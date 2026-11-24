package com.stevenpg.weatherglance.ui.location

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stevenpg.weatherglance.R
import com.stevenpg.weatherglance.data.forecast.ForecastRepository
import com.stevenpg.weatherglance.data.location.LocationSearchService
import com.stevenpg.weatherglance.data.location.PlaceResult
import com.stevenpg.weatherglance.data.settings.SettingsRepository
import com.stevenpg.weatherglance.scheduling.NotificationScheduler
import com.stevenpg.weatherglance.ui.toMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocationUiState(
    val query: String = "",
    val results: List<PlaceResult> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val savingPlace: PlaceResult? = null,
    @StringRes val error: Int? = null,
) {
    /** Error to display, including the "nothing found" case. */
    @get:StringRes
    val message: Int?
        get() = error ?: if (hasSearched && !isSearching && results.isEmpty()) R.string.location_no_results else null
}

@HiltViewModel
class LocationSearchViewModel @Inject constructor(
    private val searchService: LocationSearchService,
    private val forecastRepository: ForecastRepository,
    private val settingsRepository: SettingsRepository,
    private val scheduler: NotificationScheduler,
) : ViewModel() {

    private val state = MutableStateFlow(LocationUiState())
    val uiState: StateFlow<LocationUiState> = state.asStateFlow()

    private val savedChannel = Channel<Unit>(Channel.CONFLATED)
    val saved: Flow<Unit> = savedChannel.receiveAsFlow()

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            state.map { it.query.trim() }
                .distinctUntilChanged()
                .debounce(SEARCH_DEBOUNCE_MILLIS)
                .collectLatest { query -> if (query.length >= MIN_QUERY_LENGTH) search(query) }
        }
    }

    fun onQueryChange(query: String) = state.update { it.copy(query = query, error = null) }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun search(query: String) {
        state.update { it.copy(isSearching = true, error = null) }
        try {
            val results = searchService.search(query)
            state.update { it.copy(results = results, isSearching = false, hasSearched = true) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state.update {
                it.copy(isSearching = false, hasSearched = true, results = emptyList(), error = e.toMessageRes())
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun select(place: PlaceResult) {
        if (state.value.savingPlace != null) return
        viewModelScope.launch {
            state.update { it.copy(savingPlace = place, error = null) }
            try {
                val location = forecastRepository.resolveLocation(place.latitude, place.longitude, place.name)
                settingsRepository.update { it.copy(location = location) }
                scheduler.reschedule()
                try {
                    forecastRepository.refresh(location)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Location is saved; the home screen will show the error and offer pull-to-refresh.
                }
                savedChannel.send(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update { it.copy(error = e.toMessageRes()) }
            } finally {
                state.update { it.copy(savingPlace = null) }
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 450L
        const val MIN_QUERY_LENGTH = 2
    }
}

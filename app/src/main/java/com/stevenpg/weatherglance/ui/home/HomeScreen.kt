package com.stevenpg.weatherglance.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stevenpg.weatherglance.R
import com.stevenpg.weatherglance.domain.model.DailyForecast
import com.stevenpg.weatherglance.domain.model.WeatherAlert
import com.stevenpg.weatherglance.ui.components.ForecastStrip
import com.stevenpg.weatherglance.ui.components.PrecipLabel
import com.stevenpg.weatherglance.ui.components.WeatherIcon
import com.stevenpg.weatherglance.ui.format.ForecastFormatter
import java.time.LocalDate
import java.time.ZoneId

private const val STRIP_DAYS = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onChooseLocation: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(context.getString(it)) }
    }

    HomeContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onOpenSettings = onOpenSettings,
        onChooseLocation = onChooseLocation,
        onRefresh = viewModel::refresh,
        onNotifyNow = viewModel::notifyNow,
    )
}

/** Stateless home screen, separated from [HomeViewModel] for previews and render tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    state: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onOpenSettings: () -> Unit,
    onChooseLocation: () -> Unit,
    onRefresh: () -> Unit,
    onNotifyNow: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.location == null -> EmptyState(onChooseLocation, Modifier.padding(padding))
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                ForecastContent(
                    state = state,
                    onChooseLocation = onChooseLocation,
                    onNotifyNow = onNotifyNow,
                )
            }
        }
    }
}

@Composable
private fun ForecastContent(state: HomeUiState, onChooseLocation: () -> Unit, onNotifyNow: () -> Unit) {
    val context = LocalContext.current
    val snapshot = state.snapshot
    val zone = state.location?.zoneId() ?: ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val formatter = remember(state.unit, today) { ForecastFormatter(context, state.unit, today) }
    val days = snapshot?.daysFrom(today).orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { NotificationPermissionBanner() }
        item {
            LocationHeader(
                name = state.location?.displayName.orEmpty(),
                updated = snapshot?.let { formatter.time(it.fetchedAt) },
                onChange = onChooseLocation,
            )
        }
        items(state.alerts, key = { it.id }) { alert -> AlertCard(alert, formatter, zone) }
        item {
            Card(Modifier.fillMaxWidth()) {
                if (days.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator()
                        } else {
                            Text(stringResource(R.string.home_no_data))
                        }
                    }
                } else {
                    ForecastStrip(days.take(STRIP_DAYS), formatter, Modifier.padding(horizontal = 4.dp))
                }
            }
        }
        item { NotificationCard(state, formatter, onNotifyNow) }
        if (days.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.home_details_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(days, key = { it.date.toString() }) { day -> DayDetailRow(day, formatter) }
        }
        item {
            Text(
                stringResource(R.string.attribution),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LocationHeader(name: String, updated: String?, onChange: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.headlineSmall)
            if (updated != null) {
                Text(
                    stringResource(R.string.home_updated, updated),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onChange) { Text(stringResource(R.string.action_change)) }
    }
}

@Composable
private fun AlertCard(alert: WeatherAlert, formatter: ForecastFormatter, zone: ZoneId) {
    var expanded by rememberSaveable(alert.id) { mutableStateOf(false) }
    Card(
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(alert.event, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            alert.endsAt?.let {
                Text(
                    stringResource(R.string.alert_until, formatter.dayTime(it, zone)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            alert.headline?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            if (expanded) {
                listOfNotNull(alert.description, alert.instruction).forEach {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text(
                    stringResource(R.string.alert_tap_for_details),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun NotificationCard(state: HomeUiState, formatter: ForecastFormatter, onNotifyNow: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (state.notificationsEnabled) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                contentDescription = null,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_next_notification), style = MaterialTheme.typography.labelMedium)
                Text(
                    state.nextNotification?.let(formatter::dayAndTime)
                        ?: stringResource(R.string.home_no_notification_scheduled),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            FilledTonalButton(onClick = onNotifyNow, enabled = !state.isNotifying) {
                if (state.isNotifying) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.action_notify_now))
                }
            }
        }
    }
}

@Composable
private fun DayDetailRow(day: DailyForecast, formatter: ForecastFormatter) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            WeatherIcon(day, Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(formatter.longDayLabel(day), style = MaterialTheme.typography.titleSmall)
                Text(
                    day.shortForecast,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatter.highLow(day), style = MaterialTheme.typography.titleSmall)
                PrecipLabel(formatter.precip(day))
            }
        }
        day.details.forEach { detail ->
            Text(
                "${detail.name}: ${detail.text}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 48.dp, bottom = 4.dp),
            )
        }
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun EmptyState(onChooseLocation: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onChooseLocation) { Text(stringResource(R.string.action_choose_location)) }
    }
}

/** Explains and requests the Android 13+ notification permission, or links to settings once denied. */
@Composable
private fun NotificationPermissionBanner() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(notificationsEnabled(context)) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        enabled = notificationsEnabled(context)
    }
    LifecycleResumeEffect(Unit) {
        enabled = notificationsEnabled(context)
        onPauseOrDispose { }
    }
    if (enabled) return

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.permission_body), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                val canPrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedOnce &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                if (canPrompt) {
                    askedOnce = true
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
            }) { Text(stringResource(R.string.permission_action)) }
        }
    }
}

private fun notificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

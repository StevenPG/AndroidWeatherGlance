package com.stevenpg.weatherglance.ui.settings

import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stevenpg.weatherglance.BuildConfig
import com.stevenpg.weatherglance.R
import com.stevenpg.weatherglance.data.settings.AppSettings
import com.stevenpg.weatherglance.domain.model.AlertSeverity
import com.stevenpg.weatherglance.domain.model.TemperatureUnit
import com.stevenpg.weatherglance.notification.NotificationChannels
import com.stevenpg.weatherglance.ui.format.ForecastFormatter
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private val SEVERITY_CHOICES = listOf(AlertSeverity.MODERATE, AlertSeverity.SEVERE, AlertSeverity.EXTREME)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onChooseLocation: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val current = settings ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(R.string.settings_section_location)
            ListItem(
                headlineContent = {
                    Text(current.location?.displayName ?: stringResource(R.string.settings_location_none))
                },
                supportingContent = { Text(stringResource(R.string.settings_location_change)) },
                leadingContent = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onChooseLocation),
            )

            HorizontalDivider()
            NotificationSection(current, viewModel, onAddTime = { showTimePicker = true })

            HorizontalDivider()
            SectionHeader(R.string.settings_section_units)
            UnitSelector(current.temperatureUnit, viewModel::setTemperatureUnit)

            HorizontalDivider()
            AlertSection(current, viewModel)

            HorizontalDivider()
            SectionHeader(R.string.settings_section_about)
            ListItem(
                headlineContent = { Text(stringResource(R.string.attribution)) },
                supportingContent = { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) },
            )
        }
    }

    if (showTimePicker) {
        AddTimeDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                showTimePicker = false
                viewModel.addTime(hour, minute)
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationSection(settings: AppSettings, viewModel: SettingsViewModel, onAddTime: () -> Unit) {
    val context = LocalContext.current
    val timeFormatter = ForecastFormatter.timeFormatter(context)

    SectionHeader(R.string.settings_section_notifications)
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_notifications_enabled)) },
        supportingContent = { Text(stringResource(R.string.settings_notifications_enabled_body)) },
        trailingContent = {
            Switch(checked = settings.notificationsEnabled, onCheckedChange = viewModel::setNotificationsEnabled)
        },
    )
    if (!settings.notificationsEnabled) return

    SubHeader(R.string.settings_times)
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        settings.notificationTimes.forEach { time ->
            InputChip(
                selected = false,
                onClick = { viewModel.removeTime(time) },
                label = { Text(timeFormatter.format(time.toLocalTime())) },
                trailingIcon = {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_remove_time),
                        modifier = Modifier.size(InputChipIconSize),
                    )
                },
            )
        }
        if (settings.notificationTimes.size < AppSettings.MAX_NOTIFICATION_TIMES) {
            AssistChip(
                onClick = onAddTime,
                label = { Text(stringResource(R.string.settings_add_time)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
            )
        }
    }
    if (settings.notificationTimes.isEmpty()) {
        HintText(R.string.settings_times_empty)
    }

    SubHeader(R.string.settings_days)
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        orderedWeek().forEach { day ->
            val selected = day.value in settings.activeDays
            FilterChip(
                selected = selected,
                onClick = { viewModel.toggleDay(day) },
                label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                leadingIcon = if (selected) {
                    { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
    }

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_system_notification_settings)) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
        modifier = Modifier.clickable {
            context.startActivity(
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, NotificationChannels.FORECAST),
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitSelector(selected: TemperatureUnit, onSelect: (TemperatureUnit) -> Unit) {
    val options = listOf(
        TemperatureUnit.FAHRENHEIT to R.string.unit_fahrenheit,
        TemperatureUnit.CELSIUS to R.string.unit_celsius,
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        options.forEachIndexed { index, (unit, label) ->
            SegmentedButton(
                selected = unit == selected,
                onClick = { onSelect(unit) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(stringResource(label)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertSection(settings: AppSettings, viewModel: SettingsViewModel) {
    SectionHeader(R.string.settings_section_alerts)
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_alerts_enabled)) },
        supportingContent = { Text(stringResource(R.string.settings_alerts_enabled_body)) },
        trailingContent = { Switch(checked = settings.alertsEnabled, onCheckedChange = viewModel::setAlertsEnabled) },
    )
    if (!settings.alertsEnabled) return

    SubHeader(R.string.settings_alert_severity)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        SEVERITY_CHOICES.forEachIndexed { index, severity ->
            SegmentedButton(
                selected = severity == settings.minAlertSeverity,
                onClick = { viewModel.setMinAlertSeverity(severity) },
                shape = SegmentedButtonDefaults.itemShape(index, SEVERITY_CHOICES.size),
            ) { Text(stringResource(severityLabel(severity))) }
        }
    }
    HintText(R.string.settings_alert_severity_hint)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTimeDialog(onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = DEFAULT_PICKER_HOUR,
        initialMinute = 0,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_add_time)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun SectionHeader(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SubHeader(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun HintText(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

private fun severityLabel(severity: AlertSeverity): Int = when (severity) {
    AlertSeverity.EXTREME -> R.string.severity_extreme
    AlertSeverity.SEVERE -> R.string.severity_severe
    else -> R.string.severity_moderate
}

/** Week ordered from the locale's first day (Sunday in the US, Monday in much of the world). */
private fun orderedWeek(): List<DayOfWeek> {
    val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    return (0L until DayOfWeek.entries.size).map { first.plus(it) }
}

private val InputChipIconSize = 18.dp
private const val DEFAULT_PICKER_HOUR = 12

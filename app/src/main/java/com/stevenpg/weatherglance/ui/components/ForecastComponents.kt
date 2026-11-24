package com.stevenpg.weatherglance.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stevenpg.weatherglance.domain.model.DailyForecast
import com.stevenpg.weatherglance.notification.WeatherIcons
import com.stevenpg.weatherglance.ui.format.ForecastFormatter

@Composable
fun WeatherIcon(day: DailyForecast, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(WeatherIcons.forCondition(day.condition, night = day.isNightOnly)),
        contentDescription = null,
        modifier = modifier,
    )
}

/** One column of the five-day strip: label, icon, high, low and precipitation chance. */
@Composable
fun DayColumn(day: DailyForecast, formatter: ForecastFormatter, modifier: Modifier = Modifier) {
    val description = formatter.contentDescription(day)
    Column(
        modifier = modifier
            .padding(vertical = 8.dp)
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(formatter.dayLabel(day), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        WeatherIcon(day, Modifier.size(40.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            formatter.temperature(day.highF),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            formatter.temperature(day.lowF),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrecipLabel(formatter.precip(day))
    }
}

@Composable
fun PrecipLabel(text: String?, modifier: Modifier = Modifier) {
    // Always occupy the space so columns stay aligned.
    Row(
        modifier = modifier.alpha(if (text == null) 0f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.WaterDrop,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text ?: "0%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun ForecastStrip(days: List<DailyForecast>, formatter: ForecastFormatter, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        days.forEach { day -> DayColumn(day, formatter, Modifier.weight(1f)) }
    }
}

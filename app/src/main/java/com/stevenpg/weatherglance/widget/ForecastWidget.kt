package com.stevenpg.weatherglance.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.stevenpg.weatherglance.R
import com.stevenpg.weatherglance.data.forecast.ForecastRepository
import com.stevenpg.weatherglance.data.settings.AppSettings
import com.stevenpg.weatherglance.data.settings.SettingsRepository
import com.stevenpg.weatherglance.domain.model.DailyForecast
import com.stevenpg.weatherglance.domain.model.ForecastSnapshot
import com.stevenpg.weatherglance.notification.WeatherIcons
import com.stevenpg.weatherglance.ui.MainActivity
import com.stevenpg.weatherglance.ui.format.ForecastFormatter
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun settingsRepository(): SettingsRepository
    fun forecastRepository(): ForecastRepository
}

/** Home-screen glance: three days on a narrow widget, five once it is wide enough. */
class ForecastWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(NARROW, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val settings = entryPoint.settingsRepository().current()
        val snapshot = entryPoint.forecastRepository().cachedSnapshot()
            ?.takeIf { it.location.hasSameCoordinates(settings.location) }
        provideContent {
            GlanceTheme {
                WidgetContent(settings, snapshot)
            }
        }
    }

    private companion object {
        val NARROW = DpSize(160.dp, 72.dp)
        val WIDE = DpSize(270.dp, 72.dp)
        const val NARROW_DAYS = 3
        const val WIDE_DAYS = 5

        @Composable
        fun WidgetContent(settings: AppSettings, snapshot: ForecastSnapshot?) {
            val context = LocalContext.current
            val dayCount = if (LocalSize.current.width >= WIDE.width) WIDE_DAYS else NARROW_DAYS
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(16.dp)
                    .background(GlanceTheme.colors.widgetBackground)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (snapshot == null) {
                    Text(
                        text = context.getString(
                            if (settings.location == null) R.string.widget_setup else R.string.widget_loading,
                        ),
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
                    )
                } else {
                    val today = LocalDate.now(snapshot.location.zoneId())
                    val formatter = ForecastFormatter(context, settings.temperatureUnit, today)
                    Text(
                        text = snapshot.location.displayName,
                        maxLines = 1,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        snapshot.daysFrom(today).take(dayCount).forEach { day ->
                            DayColumn(day, formatter, GlanceModifier.defaultWeight())
                        }
                    }
                }
            }
        }

        @Composable
        fun DayColumn(day: DailyForecast, formatter: ForecastFormatter, modifier: GlanceModifier) {
            Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatter.dayLabel(day),
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp),
                )
                Image(
                    provider = ImageProvider(WeatherIcons.forCondition(day.condition, night = day.isNightOnly)),
                    contentDescription = formatter.contentDescription(day),
                    modifier = GlanceModifier.size(28.dp),
                )
                Text(
                    text = formatter.highLow(day),
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

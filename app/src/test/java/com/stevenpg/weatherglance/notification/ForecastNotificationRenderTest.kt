package com.stevenpg.weatherglance.notification

import android.app.Application
import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.stevenpg.weatherglance.R
import com.stevenpg.weatherglance.TestFixtures
import com.stevenpg.weatherglance.data.nws.AlertsResponse
import com.stevenpg.weatherglance.data.nws.ForecastResponse
import com.stevenpg.weatherglance.data.nws.NwsMapper
import com.stevenpg.weatherglance.domain.model.AlertSeverity
import com.stevenpg.weatherglance.domain.model.ForecastSnapshot
import com.stevenpg.weatherglance.domain.model.SavedLocation
import com.stevenpg.weatherglance.domain.model.TemperatureUnit
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Inflates the real notification (system decoration included) to catch RemoteViews problems that
 * would otherwise only surface as "Bad notification posted" crashes on device. Rendered PNGs are
 * written to app/build/notification-renders/ for visual review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ForecastNotificationRenderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-11-24T19:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("America/Chicago"))

    private val snapshot = ForecastSnapshot(
        location = SavedLocation(
            displayName = "Springfield, IL",
            latitude = 39.7817,
            longitude = -89.6501,
            forecastUrl = "https://api.weather.gov/gridpoints/ILX/47,55/forecast",
            timeZoneId = "America/Chicago",
        ),
        fetchedAt = now,
        days = NwsMapper.toDailyForecasts(
            TestFixtures.json.decodeFromString<ForecastResponse>(TestFixtures.read("nws/forecast.json"))
                .properties.periods,
        ),
        alerts = NwsMapper.toAlerts(
            TestFixtures.json.decodeFromString<AlertsResponse>(TestFixtures.read("nws/alerts.json")),
            now,
        ),
    )

    @Before
    fun setUp() = NotificationChannels.register(context)

    @Test
    fun `collapsed view shows three days and expanded shows five with the alert`() {
        val notification = build(fromCache = false)
        val recovered = Notification.Builder.recoverBuilder(context, notification)

        val collapsed = render(recovered.createContentView(), "collapsed")
        assertEquals(3, collapsed.findViewById<ViewGroup>(R.id.days).childCount)

        val expanded = render(recovered.createBigContentView(), "expanded")
        assertEquals(5, expanded.findViewById<ViewGroup>(R.id.days).childCount)
        val alert = expanded.findViewById<TextView>(R.id.alert)
        assertEquals(View.VISIBLE, alert.visibility)
        assertEquals("⚠ Winter Storm Watch until Thu 6:00 PM", alert.text.toString())
    }

    @Test
    fun `cells expose a spoken description and the first day is labeled Today`() {
        val expanded = render(Notification.Builder.recoverBuilder(context, build(false)).createBigContentView(), null)
        val firstCell = expanded.findViewById<ViewGroup>(R.id.days).getChildAt(0)
        assertEquals("Today", firstCell.findViewById<TextView>(R.id.day_label).text)
        assertNotNull(firstCell.contentDescription)
        assertTrue(firstCell.contentDescription.contains("high 43°"))
    }

    @Test
    fun `celsius and cached state render`() {
        val notification = ForecastNotificationFactory(context, clock).build(
            snapshot,
            TemperatureUnit.CELSIUS,
            fromCache = true,
            visibleAlertSeverity = null,
        )
        assertNotNull(notification)
        val expanded =
            render(Notification.Builder.recoverBuilder(context, notification).createBigContentView(), "celsius-cached")
        assertEquals(View.GONE, expanded.findViewById<TextView>(R.id.alert).visibility)
        assertEquals(
            "6°",
            expanded.findViewById<ViewGroup>(R.id.days).getChildAt(0).findViewById<TextView>(R.id.day_high).text,
        )
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun `renders in dark mode`() {
        val recovered = Notification.Builder.recoverBuilder(context, build(false))
        render(recovered.createContentView(), "collapsed-dark")
        render(recovered.createBigContentView(), "expanded-dark")
    }

    @Test
    fun `no notification when every forecast day is in the past`() {
        val future = Clock.fixed(Instant.parse("2026-12-15T12:00:00Z"), ZoneId.of("America/Chicago"))
        assertEquals(
            null,
            ForecastNotificationFactory(context, future).build(snapshot, TemperatureUnit.FAHRENHEIT, false, null),
        )
    }

    private fun build(fromCache: Boolean): Notification = requireNotNull(
        ForecastNotificationFactory(context, clock).build(
            snapshot,
            TemperatureUnit.FAHRENHEIT,
            fromCache = fromCache,
            visibleAlertSeverity = AlertSeverity.SEVERE,
        ),
    )

    /** Applies RemoteViews the way SystemUI does, lays them out at shade width and optionally saves a PNG. */
    private fun render(views: android.widget.RemoteViews, name: String?): View {
        val night = "night" in RuntimeEnvironment.getQualifiers().split("-")
        val parent = FrameLayout(context)
        val view = views.apply(context, parent)
        parent.addView(view)
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels - (2 * SHADE_MARGIN_DP * metrics.density).toInt()
        parent.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        parent.layout(0, 0, parent.measuredWidth, parent.measuredHeight)
        if (name != null) {
            val bitmap = Bitmap.createBitmap(parent.measuredWidth, parent.measuredHeight, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply {
                drawColor(if (night) Color.rgb(0x2D, 0x2F, 0x33) else Color.rgb(0xF3, 0xF4, 0xF8))
                parent.draw(this)
            }
            File("build/notification-renders").apply { mkdirs() }.resolve("$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)
            }
        }
        return view
    }

    private companion object {
        const val SHADE_MARGIN_DP = 12
        const val PNG_QUALITY = 100
    }
}

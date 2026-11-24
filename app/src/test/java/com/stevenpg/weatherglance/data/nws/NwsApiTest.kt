package com.stevenpg.weatherglance.data.nws

import com.stevenpg.weatherglance.BuildConfig
import com.stevenpg.weatherglance.TestFixtures
import com.stevenpg.weatherglance.di.AppModule
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlin.test.assertEquals

class NwsApiTest {

    private val server = MockWebServer()
    private lateinit var api: NwsApi

    @Before
    fun setUp() {
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(AppModule.provideOkHttp())
            .addConverterFactory(TestFixtures.json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NwsApi::class.java)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `points request identifies the app and keeps the comma unencoded`() = runTest {
        server.enqueue(MockResponse().setBody(TestFixtures.read("nws/points.json")))

        val response = api.points(NwsPoint.format(39.78172, -89.65015))

        val request = server.takeRequest()
        assertEquals("/points/39.7817,-89.6502", request.path)
        assertEquals(BuildConfig.NWS_USER_AGENT, request.getHeader("User-Agent"))
        assertEquals("application/geo+json", request.getHeader("Accept"))
        assertEquals("https://api.weather.gov/gridpoints/ILX/47,55/forecast", response.properties.forecast)
        assertEquals("America/Chicago", response.properties.timeZone)
    }

    @Test
    fun `forecast and alerts payloads parse`() = runTest {
        server.enqueue(MockResponse().setBody(TestFixtures.read("nws/forecast.json")))
        server.enqueue(MockResponse().setBody(TestFixtures.read("nws/alerts.json")))

        assertEquals(14, api.forecast(server.url("/gridpoints/ILX/47,55/forecast").toString()).properties.periods.size)
        assertEquals(1, api.activeAlerts("39.7817,-89.6501").features.size)
        server.takeRequest()
        assertEquals("/alerts/active?point=39.7817,-89.6501", server.takeRequest().path)
    }
}

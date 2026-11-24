package com.stevenpg.weatherglance.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.stevenpg.weatherglance.BuildConfig
import com.stevenpg.weatherglance.data.forecast.ForecastCache
import com.stevenpg.weatherglance.data.nws.NwsApi
import com.stevenpg.weatherglance.data.settings.AppSettings
import com.stevenpg.weatherglance.data.settings.JsonDataStoreSerializer
import com.stevenpg.weatherglance.domain.WidgetUpdater
import com.stevenpg.weatherglance.widget.GlanceWidgetUpdater
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 20L
    private const val CALL_TIMEOUT_SECONDS = 45L

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", BuildConfig.NWS_USER_AGENT)
                    .header("Accept", "application/geo+json")
                    .build(),
            )
        }
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
        }
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideNwsApi(client: OkHttpClient, json: Json): NwsApi = Retrofit.Builder()
        .baseUrl(NwsApi.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(NwsApi::class.java)

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context, json: Json): DataStore<AppSettings> =
        DataStoreFactory.create(
            serializer = JsonDataStoreSerializer(AppSettings.serializer(), AppSettings(), json),
            corruptionHandler = ReplaceFileCorruptionHandler { AppSettings() },
            produceFile = { context.dataStoreFile("settings.json") },
        )

    @Provides
    @Singleton
    fun provideForecastCacheStore(@ApplicationContext context: Context, json: Json): DataStore<ForecastCache> =
        DataStoreFactory.create(
            serializer = JsonDataStoreSerializer(ForecastCache.serializer(), ForecastCache(), json),
            corruptionHandler = ReplaceFileCorruptionHandler { ForecastCache() },
            produceFile = { context.dataStoreFile("forecast_cache.json") },
        )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun bindWidgetUpdater(impl: GlanceWidgetUpdater): WidgetUpdater
}

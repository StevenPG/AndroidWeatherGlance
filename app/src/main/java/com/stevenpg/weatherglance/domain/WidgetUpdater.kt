package com.stevenpg.weatherglance.domain

/** Abstraction so the data layer can ask home-screen widgets to re-render without depending on Glance. */
fun interface WidgetUpdater {
    suspend fun requestUpdate()
}

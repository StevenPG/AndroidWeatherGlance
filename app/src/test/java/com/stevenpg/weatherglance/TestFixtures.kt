package com.stevenpg.weatherglance

import kotlinx.serialization.json.Json

object TestFixtures {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun read(path: String): String =
        requireNotNull(javaClass.classLoader?.getResource(path)) { "Missing fixture $path" }.readText()
}

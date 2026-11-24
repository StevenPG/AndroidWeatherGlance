package com.stevenpg.weatherglance.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.stevenpg.weatherglance.ui.home.HomeScreen
import com.stevenpg.weatherglance.ui.location.LocationSearchScreen
import com.stevenpg.weatherglance.ui.settings.SettingsScreen

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val LOCATION = "location"
}

@Composable
fun WeatherGlanceNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onChooseLocation = { navController.navigate(Routes.LOCATION) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onChooseLocation = { navController.navigate(Routes.LOCATION) },
            )
        }
        composable(Routes.LOCATION) {
            LocationSearchScreen(
                onBack = { navController.popBackStack() },
                onLocationSaved = { navController.popBackStack() },
            )
        }
    }
}

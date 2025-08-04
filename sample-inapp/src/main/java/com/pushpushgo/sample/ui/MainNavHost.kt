package com.pushpushgo.sample.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pushpushgo.sample.ui.screens.InfoScreen
import com.pushpushgo.sample.ui.screens.MainScreen
import com.pushpushgo.sample.ui.screens.OfferScreen

@Composable()
internal fun MainNavHost() {
  val navController = rememberNavController()

  NavHost(navController = navController, startDestination = Screen.Main.route) {
    composable(Screen.Main.route) { MainScreen(navController) }
    composable(Screen.Info.route) { InfoScreen(navController) }
    composable(Screen.Offer.route) { OfferScreen(navController) }
  }
}

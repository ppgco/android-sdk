package com.pushpushgo.sdk.sample.inapp.ui

sealed class Screen(
  val route: String,
) {
  data object Main : Screen("main")

  data object Offer : Screen("offer")

  data object Info : Screen("info")
}

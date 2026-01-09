package com.pushpushgo.sdk.inapp.ui

data class Route(
  val name: String,
) {
  init {
    require(name.isNotBlank()) {
      "Route name must not me empty"
    }
  }
}

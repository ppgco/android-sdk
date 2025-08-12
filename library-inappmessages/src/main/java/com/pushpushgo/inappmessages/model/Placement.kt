package com.pushpushgo.inappmessages.model

import com.squareup.moshi.Json

/**
 * Enum representing the placement of the in-app message on the screen.
 */
enum class Placement {
  @Json(name = "TOP")
  TOP,

  @Json(name = "TOP_LEFT")
  TOP_LEFT,

  @Json(name = "TOP_RIGHT")
  TOP_RIGHT,

  @Json(name = "LEFT")
  LEFT,

  @Json(name = "CENTER")
  CENTER,

  @Json(name = "RIGHT")
  RIGHT,

  @Json(name = "BOTTOM")
  BOTTOM,

  @Json(name = "BOTTOM_LEFT")
  BOTTOM_LEFT,

  @Json(name = "BOTTOM_RIGHT")
  BOTTOM_RIGHT,
}

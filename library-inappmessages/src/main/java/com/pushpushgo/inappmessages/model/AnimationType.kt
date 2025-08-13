package com.pushpushgo.inappmessages.model

import com.squareup.moshi.Json

/**
 * Enum representing the animation type for the in-app message display.
 */
internal enum class AnimationType {
  @Json(name = "none")
  NONE,

  @Json(name = "appear")
  APPEAR,
}

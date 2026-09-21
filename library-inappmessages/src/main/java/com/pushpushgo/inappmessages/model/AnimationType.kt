package com.pushpushgo.inappmessages.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Enum representing the animation type for the in-app message display.
 */
@JsonClass(generateAdapter = false)
internal enum class AnimationType {
  @Json(name = "none")
  NONE,

  @Json(name = "appear")
  APPEAR,
}

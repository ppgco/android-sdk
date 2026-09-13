package com.pushpushgo.inappmessages.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Enum representing the text alignment for message components.
 */
@JsonClass(generateAdapter = false)
internal enum class Alignment {
  @Json(name = "left")
  LEFT,

  @Json(name = "center")
  CENTER,

  @Json(name = "right")
  RIGHT,

  @Json(name = "justify")
  JUSTIFY,
}

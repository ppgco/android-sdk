package com.pushpushgo.inappmessages.model

import com.squareup.moshi.Json

/**
 * Enum representing the text alignment for message components.
 */
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

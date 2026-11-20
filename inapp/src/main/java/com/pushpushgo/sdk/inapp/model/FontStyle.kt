package com.pushpushgo.sdk.inapp.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Enum representing the font style for message components.
 * 'NORMAL' corresponds to 'unstyled'.
 */
@JsonClass(generateAdapter = false)
internal enum class FontStyle {
  @Json(name = "normal")
  NORMAL,

  @Json(name = "italic")
  ITALIC,

  @Json(name = "underline")
  UNDERLINE,
}

package com.pushpushgo.inappmessages.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Enum representing where the in-app message should be displayed.
 */
@JsonClass(generateAdapter = false)
internal enum class DisplayType {
  @Json(name = "ALL")
  ALL,

  @Json(name = "SELECTED")
  SELECTED,
}

package com.pushpushgo.sdk.inapp.model

import com.squareup.moshi.Json

/**
 * Enum representing where the in-app message should be displayed.
 */
internal enum class DisplayType {
  @Json(name = "ALL")
  ALL,

  @Json(name = "SELECTED")
  SELECTED,
}

package com.pushpushgo.inappmessages.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Enum representing the behavior for showing a message again after it has been seen.
 */
@JsonClass(generateAdapter = false)
internal enum class ShowAgainType {
  @Json(name = "AFTER_TIME")
  AFTER_TIME,

  @Json(name = "NEVER")
  NEVER,
}

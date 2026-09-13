package com.pushpushgo.inappmessages.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Enum representing the target for a redirect action.
 */
@JsonClass(generateAdapter = false)
internal enum class TargetType {
  @Json(name = "_self")
  SELF,

  @Json(name = "_blank")
  BLANK,
}

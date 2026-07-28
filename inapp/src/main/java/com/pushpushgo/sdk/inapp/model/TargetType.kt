package com.pushpushgo.sdk.inapp.model

import com.squareup.moshi.Json

/**
 * Enum representing the target for a redirect action.
 */
internal enum class TargetType {
  @Json(name = "_self")
  SELF,

  @Json(name = "_blank")
  BLANK,
}

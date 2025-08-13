package com.pushpushgo.inappmessages.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data class representing the audience targeting rules for an in-app message.
 */
@JsonClass(generateAdapter = true)
internal data class InAppMessageAudience(
  @Json(name = "userType") val userType: UserAudienceType,
  @Json(name = "device") val device: List<DeviceType>,
  @Json(name = "userAgent") val userAgent: List<String>,
  @Json(name = "osType") val osType: List<OSType>,
)

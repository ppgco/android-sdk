package com.pushpushgo.inappmessages.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data class representing a single action (button) in an in-app message.
 */
@JsonClass(generateAdapter = true)
data class InAppMessageAction(
  @Json(name = "enabled") val enabled: Boolean,
  @Json(name = "actionType") val actionType: InAppActionType,
  @Json(name = "url") val url: String?,
  @Json(name = "target") val target: TargetType,
  @Json(name = "text") val text: String,
  @Json(name = "fontSize") val fontSize: Int,
  @Json(name = "fontWeight") val fontWeight: Int,
  @Json(name = "style") val style: FontStyle,
  @Json(name = "textColor") val textColor: String,
  @Json(name = "backgroundColor") val backgroundColor: String,
  @Json(name = "borderColor") val borderColor: String,
  @Json(name = "borderRadius") val borderRadius: String,
  @Json(name = "padding") val padding: String,
  @Json(name = "call") val call: String?,
)

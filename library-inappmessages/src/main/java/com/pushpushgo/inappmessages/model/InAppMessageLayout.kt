package com.pushpushgo.inappmessages.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data class representing the layout properties of an in-app message.
 */
@JsonClass(generateAdapter = true)
internal data class InAppMessageLayout(
  @Json(name = "placement")
  val placement: Placement,
  @Json(name = "margin")
  val margin: String,
  @Json(name = "padding")
  val padding: String,
  @Json(name = "paddingBody")
  val paddingBody: String,
  @Json(name = "spaceBetweenImageAndBody")
  val spaceBetweenImageAndBody: Int,
  @Json(name = "spaceBetweenContentAndActions")
  val spaceBetweenContentAndActions: Int,
  @Json(name = "spaceBetweenTitleAndDescription")
  val spaceBetweenTitleAndDescription: Int,
)

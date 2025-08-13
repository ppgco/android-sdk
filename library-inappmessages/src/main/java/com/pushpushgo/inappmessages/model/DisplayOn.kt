package com.pushpushgo.inappmessages.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data class representing a specific path/route where a message can be displayed.
 */
@JsonClass(generateAdapter = true)
internal data class DisplayOn(
  @Json(name = "display") val display: Boolean,
  @Json(name = "path") val path: String,
)

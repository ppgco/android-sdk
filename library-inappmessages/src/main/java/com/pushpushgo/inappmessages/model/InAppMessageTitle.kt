package com.pushpushgo.inappmessages.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data class representing the title properties of an in-app message.
 */
@JsonClass(generateAdapter = true)
data class InAppMessageTitle(
    @Json(name = "text") val text: String,
    @Json(name = "fontSize") val fontSize: Int,
    @Json(name = "color") val color: String,
    @Json(name = "fontWeight") val fontWeight: Int,
    @Json(name = "alignment") val alignment: Alignment,
    @Json(name = "style") val style: FontStyle
)

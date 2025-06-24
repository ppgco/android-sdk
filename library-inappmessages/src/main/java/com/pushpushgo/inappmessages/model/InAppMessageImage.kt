package com.pushpushgo.inappmessages.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data class representing the image properties of an in-app message.
 */
@JsonClass(generateAdapter = true)
data class InAppMessageImage(
    @Json(name = "url") val url: String,
    @Json(name = "hideOnMobile") val hideOnMobile: Boolean
)

package com.pushpushgo.inappmessages.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data class representing the style properties of an in-app message.
 */
@JsonClass(generateAdapter = true)
data class InAppMessageStyle(
    @Json(name = "backgroundColor") val backgroundColor: String,
    @Json(name = "borderRadius") val borderRadius: String,
    @Json(name = "border") val border: Boolean,
    @Json(name = "borderColor") val borderColor: String,
    @Json(name = "borderWidth") val borderWidth: Int,
    @Json(name = "fontFamily") val fontFamily: FontFamily,
    @Json(name = "fontUrl") val fontUrl: String?,
    @Json(name = "closeIcon") val closeIcon: Boolean,
    @Json(name = "closeIconColor") val closeIconColor: String,
    @Json(name = "closeIconWidth") val closeIconWidth: Int,
    @Json(name = "zIndex") val zIndex: Int,
    @Json(name = "animationType") val animationType: AnimationType,
    @Json(name = "dropShadow") val dropShadow: Boolean,
    @Json(name = "overlay") val overlay: Boolean
)

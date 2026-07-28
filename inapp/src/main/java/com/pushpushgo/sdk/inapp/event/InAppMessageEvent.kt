package com.pushpushgo.sdk.inapp.event

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class InAppMessageEvent(
  val action: String,
  val inApp: String,
)

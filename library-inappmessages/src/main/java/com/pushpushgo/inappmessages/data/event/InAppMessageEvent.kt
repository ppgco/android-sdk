package com.pushpushgo.inappmessages.data.event

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class InAppMessageEvent(
  val action: String,
  val inApp: String,
)

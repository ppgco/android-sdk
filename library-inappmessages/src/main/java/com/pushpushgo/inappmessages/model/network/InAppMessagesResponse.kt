package com.pushpushgo.inappmessages.model.network

import com.pushpushgo.inappmessages.model.InAppMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class InAppMessagesResponse(
  @Json(name = "data") val data: List<InAppMessage>,
  @Json(name = "metadata") val metadata: Metadata,
)

@JsonClass(generateAdapter = true)
internal data class Metadata(
  @Json(name = "total") val total: Int,
)

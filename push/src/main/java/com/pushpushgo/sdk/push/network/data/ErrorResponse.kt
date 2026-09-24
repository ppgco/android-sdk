package com.pushpushgo.sdk.push.network.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class ErrorResponse(
  val message: String? = null,
)

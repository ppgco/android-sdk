package com.pushpushgo.sdk.push.network.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class TokenUpdateRequest(
  @Json(name = "token")
  val token: String,
  @Json(name = "sdkVersion")
  val sdkVersion: String,
  @Json(name = "osVersion")
  val osVersion: String,
  @Json(name = "installationId")
  val installationId: String,
)

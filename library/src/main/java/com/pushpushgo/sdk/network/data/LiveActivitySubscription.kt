package com.pushpushgo.sdk.network.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Body sent when registering or updating a Live Activity (live notification)
 * subscriber on the backend `/core/.../live-notifications/{id}/subscribers`
 * endpoints. The same shape is reused for the endpoint-update (PUT) call.
 */
@JsonClass(generateAdapter = true)
internal data class LiveActivitySubscribeRequest(
  @Json(name = "installationId")
  val installationId: String,
  @Json(name = "installationMetadata")
  val installationMetadata: InstallationMetadata,
  @Json(name = "endpoint")
  val endpoint: LiveActivityEndpoint,
)

@JsonClass(generateAdapter = true)
internal data class InstallationMetadata(
  @Json(name = "sdkVersion")
  val sdkVersion: String,
  @Json(name = "osVersion")
  val osVersion: String,
)

@JsonClass(generateAdapter = true)
internal data class LiveActivityEndpoint(
  @Json(name = "transport")
  val transport: String,
  @Json(name = "pushToken")
  val pushToken: String,
)

/** Response returned by the subscribe (POST) call: the LA subscriber id. */
@JsonClass(generateAdapter = true)
internal data class LiveActivitySubscribeResponse(
  @Json(name = "id")
  val id: String,
)

/**
 * Body for the Live Activity statistics endpoint
 * `POST /v1/{platform}/projects/{project}/live-notifications/{liveNotification}/events`.
 */
@JsonClass(generateAdapter = true)
internal data class LiveActivityEventsRequest(
  @Json(name = "installationId")
  val installationId: String,
  @Json(name = "subscriberId")
  val subscriberId: String,
  @Json(name = "events")
  val events: List<LiveActivityEventDto>,
)

@JsonClass(generateAdapter = true)
internal data class LiveActivityEventDto(
  /** One of: `started`, `closed`, `clicked`, `clicked_1`, `clicked_2`. */
  @Json(name = "type")
  val type: String,
  /** ISO-8601 timestamp of when the event occurred. */
  @Json(name = "occurredAt")
  val occurredAt: String,
  @Json(name = "liveDataVersion")
  val liveDataVersion: Int,
)

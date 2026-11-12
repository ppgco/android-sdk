package com.pushpushgo.sdk.push.liveactivity.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// JSON blocks of Live Activity payloads, read with LiveActivityJson. Every field is
// optional: LiveActivityPayloadParser maps them to the domain models and decides
// which ones are required.

/** `configuration` block of the football match template. */
@JsonClass(generateAdapter = true)
internal data class FootballMatchConfigurationDto(
  @Json(name = "content")
  val content: FootballMatchContentDto?,
  @Json(name = "design")
  val design: FootballMatchDesignDto?,
  @Json(name = "statusLabels")
  @StatusLabels
  val statusLabels: Map<String, String>?,
  /** Alternative name of [actions]; wins when both are present. */
  @Json(name = "actionSet")
  val actionSet: List<LiveActivityActionDto?>?,
  @Json(name = "actions")
  val actions: List<LiveActivityActionDto?>?,
  @Json(name = "timeout")
  val timeout: LiveActivityTimeoutDto?,
  @Json(name = "url")
  val url: String?,
)

@JsonClass(generateAdapter = true)
internal data class FootballMatchContentDto(
  @Json(name = "title")
  val title: String?,
  @Json(name = "homeTeamName")
  val homeTeamName: String?,
  @Json(name = "homeTeamImage")
  val homeTeamImage: String?,
  @Json(name = "awayTeamName")
  val awayTeamName: String?,
  @Json(name = "awayTeamImage")
  val awayTeamImage: String?,
)

@JsonClass(generateAdapter = true)
internal data class FootballMatchDesignDto(
  @Json(name = "android")
  val android: FootballMatchAndroidDesignDto?,
)

@JsonClass(generateAdapter = true)
internal data class FootballMatchAndroidDesignDto(
  @Json(name = "hasTrackerIcon")
  val hasTrackerIcon: Boolean?,
  @Json(name = "progressBarColor")
  val progressBarColor: LiveActivityColorSetDto?,
  @Json(name = "breakTimeBarColor")
  val breakTimeBarColor: LiveActivityColorSetDto?,
)

@JsonClass(generateAdapter = true)
internal data class LiveActivityColorSetDto(
  @Json(name = "lightMode")
  val lightMode: LiveActivityColor?,
  @Json(name = "darkMode")
  val darkMode: LiveActivityColor?,
)

@JsonClass(generateAdapter = true)
internal data class LiveActivityActionDto(
  @Json(name = "type")
  val type: String?,
  @Json(name = "name")
  val name: String?,
  @Json(name = "url")
  val url: String?,
)

@JsonClass(generateAdapter = true)
internal data class LiveActivityTimeoutDto(
  @Json(name = "minutes")
  val minutes: Int?,
)

/** `liveData` block of the football match template. */
@JsonClass(generateAdapter = true)
internal data class FootballMatchLiveDataDto(
  @Json(name = "homeTeamScore")
  val homeTeamScore: Int?,
  @Json(name = "awayTeamScore")
  val awayTeamScore: Int?,
  @Json(name = "status")
  val status: String?,
  @Json(name = "statusChangedAt")
  @EpochMillis
  val statusChangedAt: Long?,
  @Json(name = "version")
  val version: Int?,
)

/** `hotMessage` block of a push. */
@JsonClass(generateAdapter = true)
internal data class HotMessageDto(
  @Json(name = "id")
  val id: String?,
  @Json(name = "text")
  val text: String?,
  @Json(name = "timestamp")
  @EpochMillis
  val timestamp: Long?,
)

/**
 * Live notification document of `GET /core/v1/android/projects/{project}/live-notifications/{id}`.
 * The template blocks stay raw JSON, in the form a push delivers them.
 */
@JsonClass(generateAdapter = true)
internal data class LiveNotificationDocumentDto(
  @Json(name = "id")
  val id: String?,
  @Json(name = "template")
  val template: String?,
  @Json(name = "configuration")
  @RawJsonObject
  val configuration: String?,
  @Json(name = "liveData")
  @RawJsonObject
  val liveData: String?,
  @Json(name = "lifecycle")
  val lifecycle: LiveNotificationLifecycleDto?,
  @Json(name = "startPolicy")
  val startPolicy: LiveNotificationStartPolicyDto?,
)

@JsonClass(generateAdapter = true)
internal data class LiveNotificationLifecycleDto(
  @Json(name = "status")
  val status: String?,
)

@JsonClass(generateAdapter = true)
internal data class LiveNotificationStartPolicyDto(
  @Json(name = "scheduledAt")
  @EpochMillis
  val scheduledAt: Long?,
  @Json(name = "countdown")
  val countdown: LiveNotificationCountdownDto?,
)

@JsonClass(generateAdapter = true)
internal data class LiveNotificationCountdownDto(
  @Json(name = "message")
  val message: String?,
)

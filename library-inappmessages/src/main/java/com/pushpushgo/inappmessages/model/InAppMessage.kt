package com.pushpushgo.inappmessages.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.ZonedDateTime

// --- Enums ---
internal enum class UserAudienceType {
  @Json(name = "ALL")
  ALL,

  @Json(name = "SUBSCRIBER")
  SUBSCRIBER,

  @Json(name = "NON_SUBSCRIBER")
  NON_SUBSCRIBER,

  @Json(name = "NOTIFICATIONS_BLOCKED")
  NOTIFICATIONS_BLOCKED,
}

internal enum class DeviceType {
  @Json(name = "ALL")
  ALL,

  @Json(name = "DESKTOP")
  DESKTOP,

  @Json(name = "MOBILE")
  MOBILE,

  @Json(name = "TABLET")
  TABLET,

  @Json(name = "OTHER")
  OTHER,
}

internal enum class OSType {
  @Json(name = "ALL")
  ALL,

  @Json(name = "ANDROID")
  ANDROID,

  @Json(name = "IOS")
  IOS,

  @Json(name = "WINDOWS")
  WINDOWS,

  @Json(name = "MACOS")
  MACOS,

  @Json(name = "HARMONY")
  HARMONY,

  @Json(name = "OTHER")
  OTHER,
}

internal enum class InAppMessageDisplayType { MODAL, BANNER, CARD, FULLSCREEN }

internal enum class TriggerType {
  @Json(name = "ENTER")
  ENTER,

  @Json(name = "CUSTOM_TRIGGER")
  CUSTOM_TRIGGER,

  @Json(name = "SCROLL")
  SCROLL,

  @Json(name = "EXIT_INTENT")
  EXIT_INTENT,
}

// For future support of intent actions
// enum class IntentActionType { VIEW, DIAL, SENDTO, SETTINGS, GEO }

// --- Data classes ---

internal data class Schedule(
  val startTime: ZonedDateTime? = null,
  val endTime: ZonedDateTime? = null,
)

@JsonClass(generateAdapter = true)
internal data class InAppMessage(
  @Json(name = "id")
  val id: String,
  @Json(name = "name")
  val name: String,
  @Json(name = "project")
  val project: String,
  @Json(name = "enabled")
  val enabled: Boolean,
  @Json(name = "createdAt")
  val createdAt: ZonedDateTime,
  @Json(name = "updatedAt")
  val updatedAt: ZonedDateTime,
  @Json(name = "deletedAt")
  val deletedAt: ZonedDateTime?,
  @Json(name = "layout")
  val layout: InAppMessageLayout,
  @Json(name = "style")
  val style: InAppMessageStyle,
  val template: String? = null,
  @Json(name = "title")
  val title: InAppMessageTitle,
  @Json(name = "description")
  val description: InAppMessageDescription,
  @Json(name = "image")
  val image: InAppMessageImage?,
  @Json(name = "actions")
  val actions: List<InAppMessageAction>,
  @Json(name = "audience")
  val audience: InAppMessageAudience,
  @Json(name = "settings")
  val settings: InAppMessageSettings,
  val dismissible: Boolean = true,
  val schedule: Schedule? = null,
  val expiration: ZonedDateTime? = null,
)

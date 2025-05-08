package com.pushpushgo.inappmessages.model

import java.time.ZonedDateTime

// --- Enums ---
enum class UserAudienceType { ALL, SUBSCRIBER, NON_SUBSCRIBER }
enum class DeviceType { ALL, DESKTOP, MOBILE, TABLET, OTHER }
enum class OSType { ALL, ANDROID, IOS, HARMONY, OTHER }
enum class ActionType { URL, INTENT }
enum class MessageType { BANNER, MODAL, TOOLTIP }
enum class TriggerType { APP_OPEN, ROUTE, CUSTOM }

// --- Data classes ---
data class Audience(
    val aud: UserAudienceType,
    val device: List<DeviceType>,
    val os: List<OSType>
)

data class InAppSettings(
    val showAfterDelay: Long = 0,
    val showAgain: Boolean = false,
    val showAgainTime: Long = 0
)

data class InAppActionPlugin(
    val actionType: ActionType,
    val payload: Map<String, Any?>
)

data class Trigger(
    val type: TriggerType,
    val route: String? = null,
    val key: String? = null,
    val value: String? = null
)


data class Schedule(
    val startTime: ZonedDateTime? = null,
    val endTime: ZonedDateTime? = null
)

data class InAppMessageStyle(
    val backgroundColor: Int? = null,
    val textColor: Int? = null
)

data class InAppMessage(
    val id: String,
    val name: String,
    val template: String,
    val actions: List<InAppActionPlugin>,
    val audience: Audience,
    val settings: InAppSettings,
    val trigger: Trigger,
    val schedule: Schedule? = null,
    val expiration: ZonedDateTime? = null,
    val dismissible: Boolean = true,
    val style: InAppMessageStyle? = null,
    val type: MessageType = MessageType.BANNER
)

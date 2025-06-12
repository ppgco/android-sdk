package com.pushpushgo.inappmessages.model

import java.time.ZonedDateTime

// --- Enums ---
enum class UserAudienceType { ALL, SUBSCRIBER, NON_SUBSCRIBER, BLOCKED_NOTIFICATIONS }
enum class DeviceType { ALL, DESKTOP, MOBILE, TABLET, OTHER }
enum class OSType { ALL, ANDROID, IOS, HARMONY, OTHER }
enum class ActionType { URL, INTENT }
enum class MessageType { BANNER, MODAL, TOOLTIP }
enum class TriggerType { APP_OPEN, ROUTE, CUSTOM }
enum class IntentActionType { VIEW, DIAL, SENDTO, SETTINGS, GEO }

// --- Data classes ---
data class Audience(
    val users: UserAudienceType,
    val device: List<DeviceType>,
    val os: List<OSType>
)

data class TimeSettings(
    val showAfterDelay: Long = 0,
    val showAgain: Boolean = false,
    val showAgainTime: Long = 0
)

data class InAppAction(
    val actionType: ActionType,
    val title: String? = null,
    // Fields for ActionType.URL
    val url: String? = null,
    // Fields for ActionType.INTENT
    val intentAction: IntentActionType? = null,
    val uri: String? = null
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

data class InAppMessage(
    val id: String,
    val name: String,
    val template: String,
    val title: String,
    val description: String,
    val image: String,
    val actions: List<InAppAction>,
    val audience: Audience,
    val timeSettings: TimeSettings,
    val trigger: Trigger,
    val dismissible: Boolean = true,
    val type: MessageType = MessageType.BANNER,
    val priority: Int,
    val schedule: Schedule? = null,
    val expiration: ZonedDateTime? = null
)

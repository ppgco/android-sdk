package com.pushpushgo.sdk.data

internal data class PushPushNotification(
    val campaignId: String,
    val notification: Notification,
    val actions: List<Action>,
    val icon: String,
    val image: String,
    val redirectLink: String
)

internal data class Notification(
    val badge: Int = 0,
    val sound: String?,
    val vibrate: Boolean = true,
    val title: String?,
    val body: String?,
    val priority: Int = 0,
    val click_action: String?
)

internal data class Action(
    val link: String,
    val action: String,
    val title: String
)

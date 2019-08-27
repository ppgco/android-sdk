package com.pushpushgo.sdk.data

internal data class PushPushNotification(
    val badge: Int = 0,
    val sound :String?,
    val vibrate: Boolean = true,
    val title: String?,
    val  body: String?,
    val priority: Int = 0,
    val click_action: String?
)
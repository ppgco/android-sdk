package com.pushpushgo.sdk.push

internal data class PushMessage(
    val from: String?,
    val data: Map<String, String>,
    val notification: PushNotification?
)

internal data class PushNotification(
    val title: String?,
    val body: String?,
    val priority: Int?,
)

package com.pushpushgo.sdk.data

import com.pushpushgo.sdk.dto.PPGoNotification

internal fun PushPushNotification.mapToDto() = PPGoNotification(
    title = notification.title,
    body = notification.body,
    priority = notification.priority,
    campaignId = campaignId,
    redirectLink = redirectLink,
)

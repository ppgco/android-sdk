package com.pushpushgo.sdk.push.data

import com.pushpushgo.sdk.push.dto.PPGoNotification

internal fun PushPushNotification.mapToDto() =
  PPGoNotification(
    title = notification.title,
    body = notification.body,
    priority = notification.priority,
    campaignId = campaignId,
    redirectLink = redirectLink,
  )

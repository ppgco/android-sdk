package com.pushpushgo.sdk.push.data

import com.pushpushgo.sdk.push.dto.PushPushGoNotification

internal fun PushPushNotification.mapToDto(): PushPushGoNotification? {
  val title = notification.title ?: return null
  val body = notification.body ?: return null

  return PushPushGoNotification(
    campaignId = campaignId,
    title = title,
    body = body,
    redirectLink = redirectLink,
    priority = notification.priority,
  )
}

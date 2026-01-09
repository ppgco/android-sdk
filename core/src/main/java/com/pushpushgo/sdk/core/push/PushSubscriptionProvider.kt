package com.pushpushgo.sdk.core.push

interface PushSubscriptionProvider {
  suspend fun subscribe()

  suspend fun unsubscribe()

  fun isSubscribed(): Boolean

  fun getSubscriberId(): String?

  fun isNotificationChannelEnabled(): Boolean
}

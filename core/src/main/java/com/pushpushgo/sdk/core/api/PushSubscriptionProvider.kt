package com.pushpushgo.sdk.core.api

interface PushSubscriptionProvider {
  suspend fun subscribe()

  suspend fun unsubscribe()

  fun isSubscribed(): Boolean

  fun getPushToken(): String?

  fun isNotificationChannelEnabled(): Boolean
}

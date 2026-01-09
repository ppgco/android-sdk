package com.pushpushgo.sdk.push.subscription

import android.content.Context
import com.pushpushgo.sdk.core.push.PushSubscriptionProvider
import com.pushpushgo.sdk.push.PushNotifications

class DefaultPushSubscriptionProvider internal constructor(
  private val context: Context,
) : PushSubscriptionProvider {
  override suspend fun subscribe() {
    PushNotifications.getInstance().registerSubscriber()
  }

  override suspend fun unsubscribe() {
    PushNotifications.getInstance().unregisterSubscriber()
  }

  override fun isSubscribed(): Boolean = PushNotifications.getInstance().isSubscribed()

  override fun getSubscriberId(): String? = PushNotifications.getInstance().getSubscriberId()

  override fun isNotificationChannelEnabled(): Boolean =
    com.pushpushgo.sdk.push.push
      .isNotificationChannelEnabled(context)
}

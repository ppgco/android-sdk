package com.pushpushgo.sdk.push.subscription

import android.app.Application
import com.pushpushgo.sdk.core.api.PushSubscriptionProvider
import com.pushpushgo.sdk.push.PushNotifications
import com.pushpushgo.sdk.push.push.isNotificationChannelEnabled as isPushNotificationChannelEnabled

class DefaultPushSubscriptionProvider internal constructor(
  private val application: Application,
) : PushSubscriptionProvider {
  override fun subscribe() = PushNotifications.subscribe()

  override fun unsubscribe() = PushNotifications.unsubscribe()

  override fun isSubscriptionRequested(): Boolean = PushNotifications.isSubscriptionRequested()

  override fun isSubscribed(): Boolean = PushNotifications.isSubscribed()

  override fun getPushToken(): String? = PushNotifications.getPushToken()

  override fun isNotificationChannelEnabled(): Boolean = isPushNotificationChannelEnabled(application)
}

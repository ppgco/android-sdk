package com.pushpushgo.sdk.push.subscription

import android.app.Application
import com.pushpushgo.sdk.core.push.PushSubscriptionProvider
import com.pushpushgo.sdk.push.PushNotifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultPushSubscriptionProvider internal constructor(
  private val application: Application,
) : PushSubscriptionProvider {
  override suspend fun subscribe() {
    withContext(Dispatchers.Main) {
      PushNotifications.getInstance().subscribeNow()
    }
  }

  override suspend fun unsubscribe() {
    withContext(Dispatchers.Main) {
      PushNotifications.getInstance().unsubscribeNow()
    }
  }

  override fun isSubscribed(): Boolean = PushNotifications.getInstance().isSubscribed()

  override fun getPushToken(): String? = PushNotifications.getInstance().getPushToken()

  override fun isNotificationChannelEnabled(): Boolean =
    com.pushpushgo.sdk.push.push
      .isNotificationChannelEnabled(application)
}

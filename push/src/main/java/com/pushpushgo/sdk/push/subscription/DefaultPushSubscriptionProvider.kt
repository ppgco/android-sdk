package com.pushpushgo.sdk.push.subscription

import android.app.Application
import com.pushpushgo.sdk.core.api.PushSubscriptionProvider
import com.pushpushgo.sdk.push.PushNotifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultPushSubscriptionProvider internal constructor(
  private val application: Application,
) : PushSubscriptionProvider {
  override suspend fun subscribe() {
    withContext(Dispatchers.Main) {
      PushNotifications.subscribe()
    }
  }

  override suspend fun unsubscribe() {
    withContext(Dispatchers.Main) {
      PushNotifications.unsubscribe()
    }
  }

  override fun isSubscribed(): Boolean = PushNotifications.isSubscribed()

  override fun getPushToken(): String? = PushNotifications.getPushToken()

  override fun isNotificationChannelEnabled(): Boolean =
    com.pushpushgo.sdk.push.push
      .isNotificationChannelEnabled(application)
}

package com.pushpushgo.sdk.push.push.service

import android.content.Context
import com.google.firebase.messaging.RemoteMessage
import com.pushpushgo.sdk.push.PushNotifications
import com.pushpushgo.sdk.push.push.PushMessage
import com.pushpushgo.sdk.push.push.PushNotification
import com.pushpushgo.sdk.push.utils.logDebug

class FcmMessagingServiceDelegate(
  private val context: Context,
) {
  private val delegate = PushNotifications.getInstance().pushNotificationsDelegate
  private val preferencesHelper = PushNotifications.getInstance().sharedPreferencesHelper

  fun onMessageReceived(remoteMessage: RemoteMessage) {
    logDebug("onMessageReceived(${remoteMessage.data})")

    delegate.onMessageReceived(
      pushMessage = remoteMessage.toPushMessage(),
      context = context,
    )
  }

  fun onNewToken(token: String) {
    delegate.onNewToken(token)
    preferencesHelper.lastToken = token
  }

  private fun RemoteMessage.toPushMessage() =
    PushMessage(
      from = from,
      data = data,
      notification =
        notification
          ?.let {
            PushNotification(
              title = it.title,
              body = it.body,
              priority = it.notificationPriority,
            )
          }.takeIf { !it?.title.isNullOrEmpty() || !it?.body.isNullOrEmpty() },
    )

  fun onDestroy() {
    delegate.onDestroy()
  }
}

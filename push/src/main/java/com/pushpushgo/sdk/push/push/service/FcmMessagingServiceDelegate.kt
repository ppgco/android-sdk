package com.pushpushgo.sdk.push.push.service

import android.content.Context
import com.google.firebase.messaging.RemoteMessage
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.push.PushMessage
import com.pushpushgo.sdk.push.push.PushNotification
import com.pushpushgo.sdk.push.push.PushNotificationDelegate
import com.pushpushgo.sdk.push.utils.logDebug

class FcmMessagingServiceDelegate(
  private val context: Context,
) {
  private val preferencesHelper by lazy { SharedPreferencesHelper(context) }

  private val delegate by lazy { PushNotificationDelegate(context) }

  fun onMessageReceived(remoteMessage: RemoteMessage) {
    logDebug("onMessageReceived(${remoteMessage.data})")
    delegate.onMessageReceived(
      pushMessage = remoteMessage.toPushMessage(),
      context = context,
    )
  }

  fun onNewToken(token: String) {
    preferencesHelper.lastFCMToken = token
    delegate.onNewToken(token)
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

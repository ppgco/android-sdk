package com.pushpushgo.sdk.push.push.service

import android.content.Context
import com.huawei.hms.push.RemoteMessage
import com.pushpushgo.sdk.push.PushNotifications
import com.pushpushgo.sdk.push.push.PushMessage
import com.pushpushgo.sdk.push.push.PushNotification
import com.pushpushgo.sdk.push.utils.logDebug

class HmsMessagingServiceDelegate(
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
      data = dataOfMap,
      notification =
        notification
          ?.let {
            PushNotification(
              title = it.title,
              body = it.body,
              priority = -1,
            )
          }.takeIf { !it?.title.isNullOrEmpty() || !it?.body.isNullOrEmpty() },
    )

  fun onDestroy() {
    logDebug("onDestroy(${this::class.java})")
//        helper.onDestroy() // on non-EMUI android killed immediately after receive message
  }
}

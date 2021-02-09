package com.pushpushgo.sdk.push.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pushpushgo.sdk.push.PushMessage
import com.pushpushgo.sdk.push.PushNotification
import com.pushpushgo.sdk.push.PushNotificationDelegate
import com.pushpushgo.sdk.network.SharedPreferencesHelper

internal class FcmMessagingService : FirebaseMessagingService() {

    private val preferencesHelper by lazy { SharedPreferencesHelper(applicationContext) }

    private val helper by lazy { PushNotificationDelegate() }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        helper.onMessageReceived(
            pushMessage = remoteMessage.toPushMessage(),
            context = applicationContext,
            isSubscribed = preferencesHelper.isSubscribed
        )
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        preferencesHelper.lastFCMToken = token
        helper.onNewToken(token, preferencesHelper.isSubscribed)
    }

    private fun RemoteMessage.toPushMessage() = PushMessage(
        from = from,
        data = data,
        notification = notification?.let {
            PushNotification(
                title = it.title,
                body = it.body,
                priority = it.notificationPriority
            )
        }
    )

    override fun onDestroy() {
        super.onDestroy()
        helper.onDestroy()
    }
}

package com.pushpushgo.sdk.push.service

import android.content.Context
import com.google.firebase.messaging.RemoteMessage
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.PushMessage
import com.pushpushgo.sdk.push.PushNotification
import com.pushpushgo.sdk.push.PushNotificationDelegate
import timber.log.Timber

class FcmMessagingServiceDelegate(private val context: Context) {

    private val preferencesHelper by lazy { SharedPreferencesHelper(context) }

    private val helper by lazy { PushNotificationDelegate() }

    fun onMessageReceived(remoteMessage: RemoteMessage) {
        Timber.tag(PushPushGo.TAG).d("onMessageReceived(%s)", remoteMessage.toString())
        helper.onMessageReceived(
            pushMessage = remoteMessage.toPushMessage(),
            context = context,
            isSubscribed = preferencesHelper.isSubscribed
        )
    }

    fun onNewToken(token: String) {
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
        }.takeIf { !it?.title.isNullOrEmpty() || !it?.body.isNullOrEmpty() }
    )

    fun onDestroy() {
        helper.onDestroy()
    }
}

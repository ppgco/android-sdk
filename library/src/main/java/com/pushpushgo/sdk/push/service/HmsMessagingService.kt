package com.pushpushgo.sdk.push.service

import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage
import com.pushpushgo.sdk.push.PushMessage
import com.pushpushgo.sdk.push.PushNotification
import com.pushpushgo.sdk.push.PushNotificationDelegate
import com.pushpushgo.sdk.network.SharedPreferencesHelper

internal class HmsMessagingService : HmsMessageService() {

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
        preferencesHelper.lastHCMToken = token
        helper.onNewToken(token, preferencesHelper.isSubscribed)
    }

    private fun RemoteMessage.toPushMessage() = PushMessage(
        from = from,
        data = dataOfMap,
        notification = notification?.let {
            PushNotification(
                title = it.title,
                body = it.body,
                priority = -1
            )
        }
    )

    override fun onDestroy() {
        super.onDestroy()
        helper.onDestroy()
    }
}

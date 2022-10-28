package com.pushpushgo.sdk.push.service

import android.content.Context
import com.huawei.hms.push.RemoteMessage
import com.pushpushgo.sdk.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.PushMessage
import com.pushpushgo.sdk.push.PushNotification
import com.pushpushgo.sdk.push.PushNotificationDelegate
import com.pushpushgo.sdk.utils.logDebug

class HmsMessagingServiceDelegate(private val context: Context) {

    private val preferencesHelper by lazy { SharedPreferencesHelper(context) }

    private val delegate by lazy { PushNotificationDelegate() }

    fun onMessageReceived(remoteMessage: RemoteMessage) {
        logDebug("onMessageReceived(${remoteMessage.data})")
        delegate.onMessageReceived(
            pushMessage = remoteMessage.toPushMessage(),
            context = context,
        )
    }

    fun onNewToken(token: String) {
        preferencesHelper.lastHCMToken = token
        delegate.onNewToken(token, preferencesHelper.isSubscribed)
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
        }.takeIf { !it?.title.isNullOrEmpty() || !it?.body.isNullOrEmpty() }
    )

    fun onDestroy() {
        logDebug("onDestroy(${this::class.java})")
//        helper.onDestroy() // on non-EMUI android killed immediately after receive message
    }
}

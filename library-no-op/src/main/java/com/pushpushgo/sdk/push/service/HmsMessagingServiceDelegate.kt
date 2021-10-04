package com.pushpushgo.sdk.push.service

import android.content.Context
import com.huawei.hms.push.RemoteMessage

@Suppress("unused", "UNUSED_PARAMETER")
class HmsMessagingServiceDelegate(private val context: Context) {

    fun onDestroy() = Unit

    fun onMessageReceived(remoteMessage: RemoteMessage) = Unit

    fun onNewToken(token: String) = Unit
}

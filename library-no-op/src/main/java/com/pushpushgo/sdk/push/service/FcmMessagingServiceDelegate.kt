package com.pushpushgo.sdk.push.service

import android.content.Context
import com.google.firebase.messaging.RemoteMessage

@Suppress("unused", "UNUSED_PARAMETER")
class FcmMessagingServiceDelegate(private val context: Context) {

    fun onDestroy() = Unit

    fun onMessageReceived(remoteMessage: RemoteMessage) = Unit

    fun onNewToken(token: String) = Unit
}

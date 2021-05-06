package com.pushpushgo.sdk.push.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

internal class FcmMessagingService : FirebaseMessagingService() {

    private val delegate by lazy { FcmMessagingServiceDelegate(applicationContext) }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        delegate.onMessageReceived(remoteMessage)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        delegate.onNewToken(token)
    }

    override fun onDestroy() {
        super.onDestroy()
        delegate.onDestroy()
    }
}

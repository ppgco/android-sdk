package com.pushpushgo.sdk.push.service

import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage

internal class HmsMessagingService : HmsMessageService() {

    private val delegate by lazy { HmsMessagingServiceDelegate(applicationContext) }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
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

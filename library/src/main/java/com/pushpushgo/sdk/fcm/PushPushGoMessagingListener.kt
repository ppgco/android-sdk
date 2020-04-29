package com.pushpushgo.sdk.fcm

import com.pushpushgo.sdk.data.Message

interface PushPushGoMessagingListener {

    fun onMessageReceived(message: Message)
}

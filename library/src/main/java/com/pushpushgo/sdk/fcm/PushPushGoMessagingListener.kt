package com.pushpushgo.sdk.fcm

import com.pushpushgo.sdk.fcm.data.Message

interface PushPushGoMessagingListener {
    fun onMessageReceived(message: Message)
}
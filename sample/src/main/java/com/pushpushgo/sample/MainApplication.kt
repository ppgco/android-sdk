package com.pushpushgo.sample

import android.app.Application
import android.util.Log
import com.pushpushgo.sdk.facade.PushPushGoFacade
import com.pushpushgo.sdk.fcm.PushPushGoMessagingListener
import com.pushpushgo.sdk.data.Message

class MainApplication: Application(), PushPushGoMessagingListener {


    override fun onCreate() {
        super.onCreate()
        PushPushGoFacade.getInstance(applicationContext).registerListener(this)

    }

    override fun onMessageReceived(message: Message) {
        Log.d(this.javaClass.simpleName,"Message: $message")
    }
}
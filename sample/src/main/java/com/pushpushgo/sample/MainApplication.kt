package com.pushpushgo.sample

import android.app.Application
import com.pushpushgo.sdk.PushPushGo

class MainApplication: Application() {

    override fun onCreate() {
        super.onCreate()
        PushPushGo.getInstance(applicationContext)
    }
}

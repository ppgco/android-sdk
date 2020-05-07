package com.pushpushgo.sample

import androidx.multidex.MultiDexApplication
import com.pushpushgo.sdk.PushPushGo

class MainApplication: MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        PushPushGo.getInstance(applicationContext)
    }
}

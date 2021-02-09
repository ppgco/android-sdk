package com.pushpushgo.sample

import androidx.multidex.MultiDexApplication
import com.pushpushgo.sdk.PushPushGo
import timber.log.Timber

class MainApplication : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        PushPushGo.getInstance(applicationContext)
    }
}

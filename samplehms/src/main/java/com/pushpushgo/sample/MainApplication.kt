package com.pushpushgo.sample

import android.app.Application
import com.pushpushgo.sdk.PushPushGo
import timber.log.Timber

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        PushPushGo.getInstance(this)
    }
}

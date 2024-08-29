package com.pushpushgo.sample

import android.app.Application
import android.content.Intent
import com.pushpushgo.sdk.PushPushGo
import timber.log.Timber

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        PushPushGo
            .getInstance(this)
            .setCustomClickIntentFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

package com.pushpushgo.sdk.sample.push.hms

import android.app.Application
import com.pushpushgo.sdk.push.PushNotifications
import timber.log.Timber

class MainApplication : Application() {
  override fun onCreate() {
    super.onCreate()

    Timber.plant(Timber.DebugTree())

    PushNotifications.initialize(this)
  }
}

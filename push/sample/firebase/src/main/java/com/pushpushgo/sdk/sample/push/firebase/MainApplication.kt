package com.pushpushgo.sdk.sample.push.firebase

import android.app.Application
import android.content.Intent
import com.pushpushgo.sdk.push.PushNotifications
import timber.log.Timber

class MainApplication : Application() {
  override fun onCreate() {
    super.onCreate()

    Timber.plant(Timber.DebugTree())

    PushNotifications
      .initialize(this)
      .setCustomClickIntentFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
  }
}

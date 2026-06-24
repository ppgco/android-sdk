package com.pushpushgo.sdk.sample.push.firebase

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.pushpushgo.sdk.push.PushNotifications
import com.pushpushgo.sdk.sample.push.firebase.activity.BeaconActivity
import com.pushpushgo.sdk.sample.push.firebase.activity.LiveActivityDemoActivity
import timber.log.Timber

class MainApplication : Application() {
  override fun onCreate() {
    super.onCreate()

    Timber.plant(Timber.DebugTree())

    PushNotifications
      .initialize(this)
      .apply {
        setCustomClickIntentFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        // Single link-routing point for regular push redirectLinks AND Live
        // Activity deep links: app://<host>/<screen> navigates in-app, anything
        // else (https etc.) resolves via the system.
        setNotificationClickHandler { _, url, flags -> routeLink(url, flags) }
      }
  }

  private fun routeLink(
    url: String,
    flags: Int,
  ) {
    Timber.tag("PPGO_SAMPLE").d("routeLink: $url")
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return

    val intent =
      when {
        uri.scheme == "app" && uri.pathSegments.firstOrNull()?.lowercase() == "beacons" ->
          Intent(this, BeaconActivity::class.java)
        uri.scheme == "app" && uri.pathSegments.firstOrNull()?.lowercase() in setOf("live-activities", "liveactivities") ->
          Intent(this, LiveActivityDemoActivity::class.java)
        else -> Intent(Intent.ACTION_VIEW, uri)
      }
    intent.addFlags(flags or Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { startActivity(intent) }
      .onFailure {
        Timber.tag("PPGO_SAMPLE").e(it, "No activity for link: $url")
        Toast.makeText(this, "Cannot open: $url", Toast.LENGTH_SHORT).show()
      }
  }
}

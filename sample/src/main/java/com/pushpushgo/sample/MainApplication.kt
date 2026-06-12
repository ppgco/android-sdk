package com.pushpushgo.sample

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.pushpushgo.sample.activity.BeaconActivity
import com.pushpushgo.sample.activity.LiveActivityDemoActivity
import com.pushpushgo.sdk.PushPushGo
import timber.log.Timber

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        PushPushGo
            .getInstance(this)
            .apply {
                setCustomClickIntentFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                // Single link-routing point for regular push redirectLinks AND
                // Live Activity deep links: app://<host>/<screen> navigates
                // in-app, anything else (https etc.) resolves via the system.
                notificationHandler = { context, url, overrideFlags ->
                    routeLink(url, overrideFlags)
                }
            }
    }

    private fun routeLink(url: String, flags: Int) {
        Timber.tag("PPGO_SAMPLE").d("routeLink: $url")
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return

        val intent = when {
            uri.scheme == "app" && uri.pathSegments.firstOrNull()?.lowercase() == "beacons" ->
                Intent(this, BeaconActivity::class.java)
            uri.scheme == "app" && uri.pathSegments.firstOrNull()?.lowercase() in setOf("live-activities", "liveactivities") ->
                Intent(this, LiveActivityDemoActivity::class.java)
            // https -> browser; other app://com.fourf.ecommerce links resolve
            // to ClickActivity through its manifest intent-filter.
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

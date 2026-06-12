package com.pushpushgo.sdk.push.liveactivity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.utils.logDebug
import com.pushpushgo.sdk.utils.logWarning

class LiveActivityDismissReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != LiveActivityHandler.ACTION_DISMISS) return

    val laId = intent.getStringExtra(LiveActivityHandler.EXTRA_LIVE_ACTIVITY_ID)
    if (laId.isNullOrBlank()) {
      logWarning("LiveActivityDismissReceiver: missing live_activity_id")
      return
    }

    logDebug("LiveActivityDismissReceiver: user dismissed $laId")

    if (PushPushGo.isInitialized()) {
      PushPushGo.getInstance().liveActivityHandler?.handleDismiss(laId)
    }
  }
}


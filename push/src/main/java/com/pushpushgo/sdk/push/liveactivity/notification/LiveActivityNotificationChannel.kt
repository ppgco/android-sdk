package com.pushpushgo.sdk.push.liveactivity.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.pushpushgo.sdk.push.R

internal object LiveActivityNotificationChannel {
  fun getChannelId(context: Context): String = context.getString(R.string.pushpushgo_live_activity_channel_id)

  fun isEnabled(context: Context): Boolean {
    val channel = NotificationManagerCompat.from(context).getNotificationChannel(getChannelId(context))
    return channel?.importance != NotificationManagerCompat.IMPORTANCE_NONE
  }

  fun ensureChannel(context: Context) {
    val id = getChannelId(context)
    val name = context.getString(R.string.pushpushgo_live_activity_channel_name)

    val channel =
      NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
        setShowBadge(false)
        enableVibration(false)
        setSound(null, null)
        description = "Real-time activity updates"
      }

    NotificationManagerCompat.from(context).createNotificationChannel(channel)
  }
}

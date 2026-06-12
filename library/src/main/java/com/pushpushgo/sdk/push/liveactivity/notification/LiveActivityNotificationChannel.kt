package com.pushpushgo.sdk.push.liveactivity.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.pushpushgo.sdk.R

internal object LiveActivityNotificationChannel {
  fun getChannelId(context: Context): String = context.getString(R.string.pushpushgo_live_activity_channel_id)

  fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
}

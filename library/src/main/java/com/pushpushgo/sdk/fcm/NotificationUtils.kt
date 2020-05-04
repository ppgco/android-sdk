package com.pushpushgo.sdk.fcm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pushpushgo.sdk.R
import com.pushpushgo.sdk.data.PushPushNotification

private var channelCreated = false

// Android O requires a Notification Channel.
fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !channelCreated) {
        val id = context.getString(R.string.notification_channel_id)
        val name = context.getString(R.string.app_name)
        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            setShowBadge(true)
            enableVibration(true)
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }
    channelCreated = true
}

internal fun createNotification(
    context: Context,
    notification: PushPushNotification,
    playSound: Boolean,
    ongoing: Boolean
) = createNotification(
    context = context,
    playSound = playSound,
    ongoing = ongoing,
    title = notification.title.orEmpty(),
    content = notification.body.orEmpty(),
    sound = notification.sound ?: "default",
    vibrate = notification.vibrate,
    priority = notification.priority,
    badge = notification.badge
)

internal fun createNotification(
    context: Context,
    title: String = context.getString(R.string.app_name),
    content: String,
    playSound: Boolean = false,
    sound: String = "default",
    vibrate: Boolean = false,
    ongoing: Boolean = false,
    priority: Int = 0,
    badge: Int = 0
): Notification {
//    val intent = Intent(this, MessagingService::class.java)
//    intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true)
//        val activityPendingIntent = PendingIntent.getActivity(
//            this, 0,
//            Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT
//        )
    return NotificationCompat.Builder(context, context.getString(R.string.notification_channel_id))
        .setContentTitle(title)
        .setContentText(content)
        .setOngoing(ongoing)
        .setPriority(priority)
        .setWhen(System.currentTimeMillis())
        .setIcon(context)
        .apply {
            if (badge > 0) setNumber(badge)

            if (vibrate) setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))

            if (playSound) {
                setAutoCancel(true)
                if (sound == "default") {
                    setDefaults(Notification.DEFAULT_ALL)
                } else {
                    setSound(Uri.parse(sound))
                }
            }
        }.build()
}

fun NotificationCompat.Builder.setIcon(context: Context): NotificationCompat.Builder {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        setSmallIcon(R.mipmap.ic_stat_notification)
        color = context.resources.getColor(R.color.colorPrimary)
    } else {
        setSmallIcon(R.mipmap.ic_stat_notification)
    }

    return this
}

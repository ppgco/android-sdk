package com.pushpushgo.sdk.fcm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pushpushgo.sdk.R
import com.pushpushgo.sdk.data.Action
import com.pushpushgo.sdk.data.PushPushNotification

private var channelCreated = false

// Android O requires a Notification Channel.
fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !channelCreated) {
        val id = context.getString(R.string.notification_channel_id)
        val name = context.getString(R.string.notification_channel_name)
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
    notify: PushPushNotification,
    playSound: Boolean,
    ongoing: Boolean
) = createNotification(
    context = context,
    playSound = playSound,
    ongoing = ongoing,
    title = notify.notification.title.orEmpty(),
    content = notify.notification.body.orEmpty(),
    sound = notify.notification.sound ?: "default",
    vibrate = notify.notification.vibrate,
    priority = notify.notification.priority,
    badge = notify.notification.badge,
    campaignId = notify.campaignId,
    actionLink = notify.redirectLink,
    clickAction = notify.notification.click_action.orEmpty(),
    actions = notify.actions
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
    badge: Int = 0,
    campaignId: String = "",
    actionLink: String = "",
    clickAction: String = "",
    actions: List<Action> = emptyList()
): Notification {
//    val intent = Intent(context, MessagingService::class.java)
//    intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true)
    return NotificationCompat.Builder(context, context.getString(R.string.notification_channel_id))
        .setContentTitle(title)
        .setContentText(content)
        .setOngoing(ongoing)
        .setPriority(priority)
        .setWhen(System.currentTimeMillis())
        .setIcon(context)
        .apply {
            if (clickAction.isNotBlank() && clickAction == "APP_PUSH_CLICK") setContentIntent(
                getClickActionIntent(context, campaignId, 0, actionLink)
            )

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

            actions.forEachIndexed { i, action ->
                val intent = getClickActionIntent(context, campaignId, i + 1, action.link)
                addAction(NotificationCompat.Action.Builder(0, action.title, intent).build())
            }
        }.build()
}

private fun getClickActionIntent(context: Context, campaignId: String, buttonId: Int, link: String) = PendingIntent.getBroadcast(
    context, buttonId, Intent(context, ClickActionReceiver::class.java).apply {
        putExtra(ClickActionReceiver.CAMPAIGN_ID, campaignId)
        putExtra(ClickActionReceiver.BUTTON_ID, buttonId)
        putExtra(ClickActionReceiver.LINK, link)
    }, PendingIntent.FLAG_UPDATE_CURRENT
)

fun NotificationCompat.Builder.setIcon(context: Context): NotificationCompat.Builder {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        setSmallIcon(R.drawable.ic_stat_notification)
        color = context.resources.getColor(R.color.colorPrimary)
    } else {
        setSmallIcon(R.drawable.ic_stat_notification)
    }

    return this
}

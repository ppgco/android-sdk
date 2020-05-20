package com.pushpushgo.sdk.fcm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.R
import com.pushpushgo.sdk.data.Action
import com.pushpushgo.sdk.data.PushPushNotification
import timber.log.Timber
import java.lang.System.currentTimeMillis
import com.pushpushgo.sdk.data.Notification as PPNotification

private val gson = Gson()

internal fun deserializeNotificationData(data: Bundle?): PushPushNotification? {
    val ppNotification = data?.getString("notification") ?: return null

    return PushPushNotification(
        campaignId = data.getString("campaign").orEmpty(),
        notification = gson.fromJson(ppNotification, PPNotification::class.java),
        actions = gson.fromJson(data.getString("actions").orEmpty(), object : TypeToken<List<Action>>() {}.type),
        icon = data.getString("icon").orEmpty(),
        image = data.getString("image").orEmpty(),
        redirectLink = data.getString("redirectLink").orEmpty()
    )
}

internal fun translateFirebasePriority(priority: Int?) = when(priority) {
    RemoteMessage.PRIORITY_HIGH -> NotificationCompat.PRIORITY_HIGH
    RemoteMessage.PRIORITY_NORMAL, RemoteMessage.PRIORITY_UNKNOWN -> NotificationCompat.PRIORITY_DEFAULT
    else -> NotificationCompat.PRIORITY_DEFAULT
}

internal fun handleNotificationLinkClick(context: Context, uri: String) {
    Intent.parseUri(uri, 0).let {
        if (it.resolveActivity(context.packageManager) != null) context.startActivity(it)
        else {
            Timber.tag(PushPushGo.TAG).e("Not found activity to open uri: %s", uri)
            Toast.makeText(context, uri, Toast.LENGTH_SHORT).show()
        }
    }
}

internal fun getUniqueNotificationId() = (currentTimeMillis() / SystemClock.uptimeMillis()).toInt()

internal fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val id = context.getString(R.string.pushpushgo_notification_default_channel_id)
        val name = context.getString(R.string.pushpushgo_notification_default_channel_name)
        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            setShowBadge(true)
            enableVibration(true)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }
}

internal fun createNotification(
    id: Int,
    context: Context,
    notify: PushPushNotification,
    playSound: Boolean,
    ongoing: Boolean,
    iconPicture: Bitmap?,
    bigPicture: Bitmap?
) = createNotification(
    id = id,
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
    actions = notify.actions,
    iconPicture = iconPicture,
    bigPicture = bigPicture
)

internal fun createNotification(
    id: Int = getUniqueNotificationId(),
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
    actions: List<Action> = emptyList(),
    iconPicture: Bitmap? = null,
    bigPicture: Bitmap? = null
): Notification {
    return NotificationCompat.Builder(context, context.getString(R.string.pushpushgo_notification_default_channel_id))
        .setContentTitle(title)
        .setContentText(content)
        .setOngoing(ongoing)
        .setPriority(priority)
        .setWhen(currentTimeMillis())
        .setLargeIcon(iconPicture)
        .setSmallIcon(R.drawable.ic_stat_pushpushgo_default)
        .setColor(ContextCompat.getColor(context, R.color.pushpushgo_notification_color_default))
        .apply {
            if (clickAction.isNotBlank() && clickAction == "APP_PUSH_CLICK") setContentIntent(
                getClickActionIntent(context, campaignId, 0, actionLink, id)
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

            setStyle(NotificationCompat.BigTextStyle().bigText(content))

            bigPicture?.let {
                setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bigPicture)
                        .bigLargeIcon(null)
                )
            }

            actions.forEachIndexed { i, action ->
                val intent = getClickActionIntent(context, campaignId, i + 1, action.link, id)
                addAction(NotificationCompat.Action.Builder(0, action.title, intent).build())
            }
        }.build()
}

private fun getClickActionIntent(context: Context, campaignId: String, buttonId: Int, link: String, id: Int) =
    PendingIntent.getBroadcast(
        context, buttonId, Intent(context, ClickActionReceiver::class.java).apply {
            putExtra(ClickActionReceiver.NOTIFICATION_ID, id)
            putExtra(ClickActionReceiver.CAMPAIGN_ID, campaignId)
            putExtra(ClickActionReceiver.BUTTON_ID, buttonId)
            putExtra(ClickActionReceiver.LINK, link)
        }, PendingIntent.FLAG_UPDATE_CURRENT
    )

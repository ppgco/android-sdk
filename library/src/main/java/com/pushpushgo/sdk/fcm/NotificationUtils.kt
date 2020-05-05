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
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.R
import com.pushpushgo.sdk.data.Action
import com.pushpushgo.sdk.data.PushPushNotification
import timber.log.Timber

private val gson = Gson()

internal fun deserializeNotificationData(data: Map<String, String>) = PushPushNotification(
    campaignId = data["campaign"].orEmpty(),
    notification = gson.fromJson(data["notification"], com.pushpushgo.sdk.data.Notification::class.java),
    actions = gson.fromJson(data["actions"], object : TypeToken<List<Action>>() {}.type),
    icon = data["icon"].orEmpty(),
    image = data["image"].orEmpty(),
    redirectLink = data["redirectLink"].orEmpty()
)

internal fun deserializeNotificationData(data: Bundle?) = PushPushNotification(
    campaignId = data?.getString("campaign").orEmpty(),
    notification = gson.fromJson(data?.getString("notification").orEmpty(), com.pushpushgo.sdk.data.Notification::class.java),
    actions = gson.fromJson(data?.getString("actions").orEmpty(), object : TypeToken<List<Action>>() {}.type),
    icon = data?.getString("icon").orEmpty(),
    image = data?.getString("image").orEmpty(),
    redirectLink = data?.getString("redirectLink").orEmpty()
)

internal fun handleNotificationLinkClick(context: Context, uri: String) {
    Intent.parseUri(uri, 0).let {
        if (it.resolveActivity(context.packageManager) != null) context.startActivity(it)
        else {
            Timber.tag(PushPushGo.TAG).e("Not found activity to open uri: %s", uri)
            Toast.makeText(context, uri, Toast.LENGTH_SHORT).show()
        }
    }
}

private var channelCreated = false

// Android O requires a Notification Channel.
internal fun createNotificationChannel(context: Context) {
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
    ongoing: Boolean,
    iconPicture: Bitmap?,
    bigPicture: Bitmap?
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
    actions = notify.actions,
    iconPicture = iconPicture,
    bigPicture = bigPicture
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
    actions: List<Action> = emptyList(),
    iconPicture: Bitmap? = null,
    bigPicture: Bitmap? = null
): Notification {
//    val intent = Intent(context, MessagingService::class.java)
//    intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true)
    return NotificationCompat.Builder(context, context.getString(R.string.notification_channel_id))
        .setContentTitle(title)
        .setContentText(content)
        .setOngoing(ongoing)
        .setPriority(priority)
        .setWhen(System.currentTimeMillis())
        .setLargeIcon(iconPicture)
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

            bigPicture?.let {
                setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bigPicture)
                        .bigLargeIcon(null)
                )
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

private fun NotificationCompat.Builder.setIcon(context: Context): NotificationCompat.Builder {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        setSmallIcon(R.drawable.ic_stat_notification)
        color = context.resources.getColor(R.color.colorPrimary)
    } else {
        setSmallIcon(R.drawable.ic_stat_notification)
    }

    return this
}

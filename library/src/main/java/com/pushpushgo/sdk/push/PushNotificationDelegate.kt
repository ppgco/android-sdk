package com.pushpushgo.sdk.push

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.R
import com.pushpushgo.sdk.data.Action
import com.pushpushgo.sdk.data.EventType
import com.pushpushgo.sdk.data.PushPushNotification
import com.pushpushgo.sdk.utils.mapToBundle
import kotlinx.coroutines.*
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

internal class PushNotificationDelegate : CoroutineScope {

    private val errorHandler = CoroutineExceptionHandler { _, throwable -> Timber.tag(PushPushGo.TAG).e(throwable) }

    private var job: Job = Job()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    fun onMessageReceived(pushMessage: PushMessage, context: Context, isSubscribed: Boolean) {
        job = Job()
        Timber.tag(PushPushGo.TAG).d("From: %s", pushMessage.from)

        launch(errorHandler) {
            val notificationId = getUniqueNotificationId()

            val notification = when {
                pushMessage.data.isNotEmpty() -> getDataNotification(
                    context = context,
                    remoteMessage = pushMessage,
                    notificationId = notificationId,
                    isSubscribed = isSubscribed
                )
                pushMessage.notification != null -> sendSimpleNotification(
                    context = context,
                    remoteMessage = pushMessage,
                    notificationId = notificationId
                )
                else -> throw IllegalStateException("Unknown notification type")
            }

            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    fun onNewToken(token: String, isSubscribed: Boolean) {
        Timber.tag(PushPushGo.TAG).d("Refreshed token: $token")

        if (PushPushGo.isInitialized() && isSubscribed) {
            PushPushGo.getInstance().registerSubscriber()
        }
    }

    fun onDestroy() {
        job.cancel()
    }

    private fun getUniqueNotificationId() = (System.currentTimeMillis() / SystemClock.uptimeMillis()).toInt()

    private suspend fun getDataNotification(
        context: Context,
        remoteMessage: PushMessage,
        notificationId: Int,
        isSubscribed: Boolean
    ): Notification {
        Timber.tag(PushPushGo.TAG).d("Message data payload: %s", remoteMessage.data)

        val pushPushNotification = deserializeNotificationData(remoteMessage.data.mapToBundle())
            ?: return sendSimpleNotification(context, remoteMessage, notificationId)

        sendDeliveredEvent(pushPushNotification.campaignId, isSubscribed)
        return createDataNotification(context, notificationId, pushPushNotification)
    }

    private fun sendSimpleNotification(context: Context, remoteMessage: PushMessage, notificationId: Int): Notification {
        Timber.tag(PushPushGo.TAG).d("Message notification title: %s", remoteMessage.notification?.title)

        return createNotification(
            id = notificationId,
            context = context,
            title = remoteMessage.notification?.title!!,
            content = remoteMessage.notification.body!!,
            priority = translateFirebasePriority(remoteMessage.notification.priority)
        )
    }

    private fun translateFirebasePriority(priority: Int?) = when (priority) {
        1 -> NotificationCompat.PRIORITY_HIGH
        2, 0 -> NotificationCompat.PRIORITY_DEFAULT
        else -> NotificationCompat.PRIORITY_DEFAULT
    }

    private fun sendDeliveredEvent(campaignId: String, isSubscribed: Boolean) {
        if (PushPushGo.isInitialized() && isSubscribed) {
            PushPushGo.getInstance().getUploadManager().sendEvent(
                type = EventType.DELIVERED,
                buttonId = 0,
                campaign = campaignId
            )
        }
    }

    private suspend fun createDataNotification(
        context: Context,
        notificationId: Int,
        notification: PushPushNotification
    ) = createNotification(
        id = notificationId,
        context = context,
        notify = notification,
        playSound = true,
        ongoing = false,
        bigPicture = getBitmapFromUrl(notification.image),
        iconPicture = getBitmapFromUrl(notification.icon)
    )

    private suspend fun getBitmapFromUrl(url: String?): Bitmap? {
        try {
            return withTimeoutOrNull(5000) {
                PushPushGo.getInstance().getNetwork().getBitmapFromUrl(url)
            }
        } catch (e: Throwable) {
            Timber.tag(PushPushGo.TAG).e(e, "Failed to download bitmap picture")
        }

        return null
    }

    private fun createNotification(
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
        vibrate = notify.notification.vibrate.toBoolean(),
        priority = notify.notification.priority,
        badge = notify.notification.badge,
        campaignId = notify.campaignId,
        actionLink = notify.redirectLink,
        clickAction = notify.notification.click_action.orEmpty(),
        actions = notify.actions,
        iconPicture = iconPicture,
        bigPicture = bigPicture
    )

    private fun createNotification(
        id: Int,
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
    ) = NotificationCompat.Builder(context, context.getString(R.string.pushpushgo_notification_default_channel_id))
        .setContentTitle(title)
        .setContentText(content)
        .setOngoing(ongoing)
        .setPriority(priority)
        .setWhen(System.currentTimeMillis())
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

    private fun getClickActionIntent(context: Context, campaignId: String, buttonId: Int, link: String, id: Int) =
        PendingIntent.getBroadcast(
            context, buttonId, Intent(context, ClickActionReceiver::class.java).apply {
                putExtra(ClickActionReceiver.NOTIFICATION_ID, id)
                putExtra(ClickActionReceiver.CAMPAIGN_ID, campaignId)
                putExtra(ClickActionReceiver.BUTTON_ID, buttonId)
                putExtra(ClickActionReceiver.LINK, link)
            }, PendingIntent.FLAG_UPDATE_CURRENT
        )
}

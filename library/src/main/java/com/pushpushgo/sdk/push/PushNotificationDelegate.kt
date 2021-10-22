package com.pushpushgo.sdk.push

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.R
import com.pushpushgo.sdk.data.Action
import com.pushpushgo.sdk.data.EventType
import com.pushpushgo.sdk.data.PushPushNotification
import com.pushpushgo.sdk.utils.mapToBundle
import com.pushpushgo.sdk.work.UploadDelegate
import kotlinx.coroutines.*
import timber.log.Timber
import kotlin.random.Random

internal class PushNotificationDelegate {

    private val uploadDelegate by lazy { UploadDelegate() }

    private val errorHandler = CoroutineExceptionHandler { _, throwable -> Timber.tag(PushPushGo.TAG).e(throwable) }

    private val job = SupervisorJob()
    private val delegateScope = CoroutineScope(job + Dispatchers.Main)

    fun onMessageReceived(pushMessage: PushMessage, context: Context, isSubscribed: Boolean) {
        Timber.tag(PushPushGo.TAG).d("From: %s", pushMessage.from)

        delegateScope.launch(errorHandler) {
            val notificationId = getUniqueNotificationId()
            val projectId = PushPushGo.getInstance().getProjectId()
            Timber.tag(PushPushGo.TAG).d("Notification unique id: $notificationId")

            val notification = when {
                pushMessage.data.isNotEmpty() -> getDataNotification(
                    context = context,
                    remoteMessage = pushMessage,
                    notificationId = notificationId,
                    isSubscribed = isSubscribed,
                    projectId = projectId,
                )
                pushMessage.notification != null -> sendSimpleNotification(
                    context = context,
                    remoteMessage = pushMessage,
                    notificationId = notificationId,
                    projectId = projectId,
                )
                else -> throw IllegalStateException("Unknown notification type")
            }

            NotificationManagerCompat.from(context).notify(notificationId, notification)
            Timber.tag(PushPushGo.TAG).d("Notification sent: $notificationId => $notification")
        }
    }

    fun onNewToken(token: String, isSubscribed: Boolean) {
        Timber.tag(PushPushGo.TAG).d("Refreshed token: $token")

        if (PushPushGo.isInitialized() && isSubscribed) {
            PushPushGo.getInstance().getUploadManager().sendRegister(token)
        }
    }

    fun onDestroy() {
        job.cancelChildren()
    }

    private fun getUniqueNotificationId() = Random.nextInt(0, Int.MAX_VALUE)

    private suspend fun getDataNotification(
        context: Context,
        remoteMessage: PushMessage,
        notificationId: Int,
        isSubscribed: Boolean,
        projectId: String,
    ): Notification {
        Timber.tag(PushPushGo.TAG).d("Message data payload: %s", remoteMessage.data)

        val pushPushNotification = deserializeNotificationData(remoteMessage.data.mapToBundle())
            ?: return sendSimpleNotification(context, remoteMessage, notificationId, projectId)

        sendDeliveredEvent(pushPushNotification.campaignId, isSubscribed)
        return createDataNotification(context, notificationId, pushPushNotification, projectId)
    }

    private fun sendSimpleNotification(
        context: Context,
        remoteMessage: PushMessage,
        notificationId: Int,
        projectId: String,
    ): Notification {
        Timber.tag(PushPushGo.TAG).d("Message notification title: %s", remoteMessage.notification?.title)

        return createNotification(
            id = notificationId,
            context = context,
            projectId = projectId,
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
            uploadDelegate.sendEvent(
                type = EventType.DELIVERED,
                buttonId = 0,
                campaign = campaignId
            )
        }
    }

    private suspend fun createDataNotification(
        context: Context,
        notificationId: Int,
        notification: PushPushNotification,
        projectId: String,
    ) = createNotification(
        id = notificationId,
        context = context,
        notify = notification,
        playSound = true,
        ongoing = false,
        projectId = projectId,
        bigPicture = getBitmapFromUrl(notification.image),
        iconPicture = getBitmapFromUrl(notification.icon)
    )

    private suspend fun getBitmapFromUrl(url: String?): Bitmap? {
        try {
            return withTimeoutOrNull(5000) {
                withContext(Dispatchers.IO) {
                    PushPushGo.getInstance().getNetwork().getBitmapFromUrl(url)
                }
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
        projectId: String,
        iconPicture: Bitmap?,
        bigPicture: Bitmap?,
    ) = createNotification(
        id = id,
        context = context,
        playSound = playSound,
        ongoing = ongoing,
        projectId = projectId,
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
        projectId: String,
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
        bigPicture: Bitmap? = null,
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
                getClickActionIntent(context, campaignId, 0, actionLink, id, projectId)
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
                val intent = getClickActionIntent(context, campaignId, i + 1, action.link, id, projectId)
                addAction(NotificationCompat.Action.Builder(0, action.title, intent).build())
            }
        }.build()

    private fun getClickActionIntent(
        context: Context,
        campaignId: String,
        buttonId: Int,
        link: String,
        id: Int,
        projectId: String,
    ) = PendingIntent.getBroadcast(
        context, getUniqueNotificationId(), Intent(context, ClickActionReceiver::class.java).apply {
            putExtra(ClickActionReceiver.NOTIFICATION_ID, id)
            putExtra(ClickActionReceiver.CAMPAIGN_ID, campaignId)
            putExtra(ClickActionReceiver.BUTTON_ID, buttonId)
            putExtra(ClickActionReceiver.PROJECT_ID, projectId)
            putExtra(ClickActionReceiver.LINK, link)
        }, PendingIntent.FLAG_UPDATE_CURRENT
    )
}

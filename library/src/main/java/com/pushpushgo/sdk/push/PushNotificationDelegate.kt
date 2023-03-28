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
import com.pushpushgo.sdk.utils.*
import com.pushpushgo.sdk.work.UploadDelegate
import kotlinx.coroutines.*
import kotlin.random.Random

internal class PushNotificationDelegate {

    private val uploadDelegate by lazy { UploadDelegate() }

    private val errorHandler = CoroutineExceptionHandler { _, throwable -> logError(throwable) }

    private val job = SupervisorJob()
    private val delegateScope = CoroutineScope(job + Dispatchers.Main)

    fun onMessageReceived(pushMessage: PushMessage, context: Context) {
        logDebug("From: ${pushMessage.from}")

        if (!PushPushGo.getInstance().isPPGoPush(pushMessage.data)) {
            return logWarning("Push is not from PPGo")
        }

        val pushProjectId = pushMessage.data["project"].orEmpty()
        val pushSubscriberId = pushMessage.data["subscriber"].orEmpty()
        val initializedProjectId = PushPushGo.getInstance().getProjectId()
        if (pushProjectId != initializedProjectId) {
            PushPushGo.getInstance().onInvalidProjectIdHandler(pushProjectId, pushSubscriberId, initializedProjectId)
        } else processPushMessage(pushMessage, context)
    }

    private fun processPushMessage(pushMessage: PushMessage, context: Context) {
        val notificationManager = NotificationManagerCompat.from(context)

        if (!notificationManager.areNotificationsEnabled()) {
            return logWarning("Push notifications are disabled by user")
        }

        delegateScope.launch(errorHandler) {
            val notificationId = getUniqueNotificationId()
            logDebug("Notification unique id: $notificationId")

            val notification = when {
                pushMessage.data.isNotEmpty() -> getDataNotification(
                    context = context,
                    remoteMessage = pushMessage,
                    notificationId = notificationId,
                )
                pushMessage.notification != null -> getSimpleNotification(
                    context = context,
                    remoteMessage = pushMessage,
                    notificationId = notificationId,
                )
                else -> throw IllegalStateException("Unknown notification type")
            }

            notificationManager.notify(notificationId, notification)
            logDebug("Notification sent: $notificationId => $notification")
        }
    }

    fun onNewToken(token: String, isSubscribed: Boolean) {
        logDebug("Refreshed token: $token")

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
    ): Notification {
        val pushPushNotification = deserializeNotificationData(remoteMessage.data.mapToBundle())
            ?: return getSimpleNotification(context, remoteMessage, notificationId)

        sendDeliveredEvent(pushPushNotification)
        return createDataNotification(context, notificationId, pushPushNotification)
    }

    private fun getSimpleNotification(
        context: Context,
        remoteMessage: PushMessage,
        notificationId: Int,
    ): Notification {
        logDebug("Message notification title: ${remoteMessage.notification?.title}")

        return createNotification(
            id = notificationId,
            context = context,
            projectId = PushPushGo.getInstance().getProjectId(),
            subscriberId = remoteMessage.data["subscriber"].orEmpty(),
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

    private fun sendDeliveredEvent(notification: PushPushNotification) {
        if (PushPushGo.isInitialized() && PushPushGo.getInstance().isSubscribed()) {
            uploadDelegate.sendEvent(
                type = EventType.DELIVERED,
                buttonId = 0,
                projectId = notification.project,
                subscriberId = notification.subscriber,
                campaign = notification.campaignId,
            )
        }
    }

    private suspend fun createDataNotification(
        context: Context,
        notificationId: Int,
        notification: PushPushNotification,
    ) = createNotification(
        id = notificationId,
        context = context,
        notify = notification,
        playSound = true,
        ongoing = false,
        projectId = notification.project,
        subscriberId = notification.subscriber,
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
            logError("Failed to download bitmap picture", e)
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
        subscriberId: String,
        iconPicture: Bitmap?,
        bigPicture: Bitmap?,
    ) = createNotification(
        id = id,
        context = context,
        playSound = playSound,
        ongoing = ongoing,
        projectId = projectId,
        subscriberId = subscriberId,
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
        subscriberId: String,
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
            val launcherIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)

            if (clickAction.isNotBlank() && clickAction == "APP_PUSH_CLICK" && launcherIntent != null) {
                setContentIntent(
                    getClickActionIntent(
                        context = context,
                        campaignId = campaignId,
                        buttonId = 0,
                        link = actionLink,
                        id = id,
                        projectId = projectId,
                        subscriberId = subscriberId,
                        launcherIntent = launcherIntent
                    )
                )
            }

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

            launcherIntent?.let {
                actions.forEachIndexed { index, action ->
                    val intent = getClickActionIntent(
                        context = context,
                        campaignId = campaignId,
                        buttonId = index + 1,
                        link = action.link,
                        id = id,
                        projectId = projectId,
                        subscriberId = subscriberId,
                        launcherIntent = it
                    )

                    addAction(NotificationCompat.Action.Builder(0, action.title, intent).build())
                }
            }
        }.build()

    private fun getClickActionIntent(
        context: Context,
        campaignId: String,
        buttonId: Int,
        link: String,
        id: Int,
        projectId: String,
        subscriberId: String,
        launcherIntent: Intent,
    ) = PendingIntent.getActivity(
        context,
        getUniqueNotificationId(),
        launcherIntent.apply {
            putExtra(NOTIFICATION_ID_EXTRA, id)
            putExtra(CAMPAIGN_ID_EXTRA, campaignId)
            putExtra(BUTTON_ID_EXTRA, buttonId)
            putExtra(PROJECT_ID_EXTRA, projectId)
            putExtra(SUBSCRIBER_ID_EXTRA, subscriberId)
            putExtra(LINK_EXTRA, link)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntentCompat.FLAG_IMMUTABLE
    )

    companion object {
        const val NOTIFICATION_ID_EXTRA = "notification_id"
        const val CAMPAIGN_ID_EXTRA = "campaign"
        const val BUTTON_ID_EXTRA = "button"
        const val PROJECT_ID_EXTRA = "project"
        const val SUBSCRIBER_ID_EXTRA = "subscriber"
        const val LINK_EXTRA = "link"
    }
}

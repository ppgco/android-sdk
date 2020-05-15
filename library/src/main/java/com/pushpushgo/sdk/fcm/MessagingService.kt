package com.pushpushgo.sdk.fcm

import android.app.Notification
import android.graphics.Bitmap
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.data.EventType
import com.pushpushgo.sdk.data.PushPushNotification
import com.pushpushgo.sdk.network.SharedPreferencesHelper
import com.pushpushgo.sdk.utils.mapToBundle
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber

internal class MessagingService : FirebaseMessagingService() {

    private val preferencesHelper by lazy { SharedPreferencesHelper(applicationContext) }

    private val errorHandler = CoroutineExceptionHandler { _, throwable -> Timber.tag(PushPushGo.TAG).e(throwable) }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Timber.tag(PushPushGo.TAG).d("From: %s", remoteMessage.from)

        GlobalScope.launch(errorHandler) {
            val notificationId = getUniqueNotificationId()

            val notification = when {
                // Check if message contains a data payload
                remoteMessage.data.isNotEmpty() -> {
                    Timber.tag(PushPushGo.TAG).d("Message data payload: %s", remoteMessage.data)

                    val pushPushNotification = deserializeNotificationData(remoteMessage.data.mapToBundle())
                    sendDeliveredEvent(pushPushNotification.campaignId)
                    createDataNotification(notificationId, pushPushNotification)
                }
                // Check if message contains a notification payload
                remoteMessage.notification != null -> {
                    Timber.tag(PushPushGo.TAG).d("Message notification title: %s", remoteMessage.notification?.title)

                    createNotification(
                        context = baseContext,
                        title = remoteMessage.notification?.title!!,
                        content = remoteMessage.notification?.body!!,
                        priority = translateFirebasePriority(remoteMessage.notification?.notificationPriority)
                    )
                }
                else -> throw IllegalStateException("Unknown notification type")
            }

            NotificationManagerCompat.from(baseContext).notify(notificationId, notification)
        }
    }

    private fun sendDeliveredEvent(campaignId: String) {
        if (PushPushGo.isInitialized() && preferencesHelper.isSubscribed) {
            PushPushGo.getInstance().getUploadManager().sendEvent(
                type = EventType.DELIVERED,
                buttonId = 0,
                campaign = campaignId
            )
        }
    }

    private suspend fun createDataNotification(notificationId: Int, notification: PushPushNotification): Notification {
        return createNotification(
            id = notificationId,
            context = baseContext,
            notify = notification,
            playSound = true,
            ongoing = false,
            bigPicture = getBitmapFromUrl(notification.image),
            iconPicture = getBitmapFromUrl(notification.icon)
        )
    }

    private suspend fun getBitmapFromUrl(url: String?): Bitmap? {
        if (PushPushGo.isInitialized())
            try {
                return withTimeout(9000) {
                    PushPushGo.getInstance().getNetwork().getDrawable(url)
                }
            } catch (e: Throwable) {
                Timber.tag(PushPushGo.TAG).e(e, "Failed to download bitmap picture")
            }

        return null
    }

    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the InstanceID token
     * is initially generated so this is where you would retrieve the token.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.tag(PushPushGo.TAG).d("Refreshed token: $token")

        preferencesHelper.lastToken = token
        if (PushPushGo.isInitialized() && preferencesHelper.isSubscribed) {
            PushPushGo.getInstance().registerSubscriber()
        }
    }
}

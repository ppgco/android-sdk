package com.pushpushgo.sdk.fcm

import android.graphics.Bitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_HIGH
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.data.EventType
import com.pushpushgo.sdk.network.SharedPreferencesHelper
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber

internal class MessagingService : FirebaseMessagingService() {

    companion object {
        private const val EXTRA_STARTED_FROM_NOTIFICATION = "extra:started_from_notification"

        private const val EXTRA_STOP_SERVICE = "extra:stop_service"

        const val NOTIFICATION_ID = 1958643221
    }

    private val preferencesHelper by lazy { SharedPreferencesHelper(applicationContext) }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Timber.tag(PushPushGo.TAG).d("From: %s", remoteMessage.from)

        createNotificationChannel(applicationContext)

        val notificationManager = NotificationManagerCompat.from(baseContext)
        when {
            // Check if message contains a data payload
            remoteMessage.data.isNotEmpty() -> {
                Timber.tag(PushPushGo.TAG).d("Message data payload: %s", remoteMessage.data)

                val notify = deserializeNotificationData(remoteMessage.data)
                GlobalScope.launch {
                    notificationManager.notify(
                        NOTIFICATION_ID, createNotification(
                            context = baseContext,
                            notify = notify,
                            playSound = true,
                            ongoing = false,
                            bigPicture = getBitmapFromUrl(notify.image),
                            iconPicture = getBitmapFromUrl(notify.icon)
                        )
                    )
                }
                sendDeliveredEvent(remoteMessage.data["campaign"].orEmpty())
            }
            // Check if message contains a notification payload
            remoteMessage.notification != null -> {
                Timber.tag(PushPushGo.TAG).d("Message Notification Body: %s", remoteMessage.notification?.body)

                val notification = createNotification(
                    context = baseContext,
                    title = remoteMessage.notification?.title!!,
                    content = remoteMessage.notification?.body!!,
                    priority = IMPORTANCE_HIGH
                )

                notificationManager.notify(NOTIFICATION_ID, notification)
//                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun sendDeliveredEvent(campaignId: String) {
        if (PushPushGo.isInitialized() && preferencesHelper.isSubscribed) {
            GlobalScope.launch {
                PushPushGo.getInstance().getNetwork().sendEvent(
                    type = EventType.DELIVERED,
                    buttonId = 0,
                    campaign = campaignId
                )
            }
        }
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
            GlobalScope.launch { PushPushGo.getInstance().getNetwork().registerToken() }
        }
    }
}

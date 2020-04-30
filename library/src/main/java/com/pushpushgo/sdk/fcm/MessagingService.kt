package com.pushpushgo.sdk.fcm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.R
import com.pushpushgo.sdk.data.Message
import com.pushpushgo.sdk.data.PushPushNotification
import com.pushpushgo.sdk.exception.PushPushException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber

internal class MessagingService : FirebaseMessagingService() {

    companion object {
        private const val EXTRA_STARTED_FROM_NOTIFICATION = "extra:started_from_notification"

        private const val EXTRA_STOP_SERVICE = "extra:stop_service"

        private const val NOTIFICATION_ID = 1958643221
    }

    private var channelNotCreated = true

    private var notificationManager: NotificationManager? = null

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        try {
            val listener = PushPushGo.INSTANCE?.getListener()
            val msg = Message(remoteMessage.from, remoteMessage.data, remoteMessage.notification?.body)
            listener?.onMessageReceived(msg)
            Timber.tag(PushPushGo.TAG).d("Message sent to listener: $msg$")
        } catch (ex: PushPushException) {
            // handle remote message inside library
            if (channelNotCreated) {
                createNotificationWithBadges()
            }
            Timber.tag(PushPushGo.TAG).d("From: %s", remoteMessage.from!!)

            // Check if message contains a data payload.
            if (remoteMessage.data.isNotEmpty()) {
                Timber.tag(PushPushGo.TAG).d("Message data payload: %s", remoteMessage.data)
                val gson = Gson()
                val notif = gson.fromJson(
                    remoteMessage.data["notification"],
                    PushPushNotification::class.java
                )
                NotificationManagerCompat
                    .from(this)
                    .notify(
                        1746,
                        NotificationUtils.createNotification(
                            this,
                            notif,
                            playSound = true,
                            ongoing = false
                        )
                    )


            }

            // Check if message contains a notification payload.
            if (remoteMessage.notification != null) {
                Timber.tag(PushPushGo.TAG).d("Message Notification Body: %s", remoteMessage.notification!!.body!!)
                val notification = getNotification(remoteMessage.notification!!.body!!)

                notificationManager?.notify(NOTIFICATION_ID, notification)
                startForeground(NOTIFICATION_ID, notification)
            }

            // Also if you intend on generating your own notifications as a result of a received FCM
            // message, here is where that should be initiated. See sendNotification method below.
        }
    }

    private fun createNotificationWithBadges() {
        channelNotCreated = false
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Android O requires a Notification Channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.app_name)
            val channel = NotificationChannel(
                getString(R.string.notification_channel_id), name, IMPORTANCE_HIGH
            ).apply {
                setShowBadge(true)
            }
            channel.enableVibration(true)
            notificationManager!!.createNotificationChannel(channel)
        }
    }

    private fun createNotificationChannel() {
        channelNotCreated = false
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Android O requires a Notification Channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.app_name)
            val channel = NotificationChannel(getString(R.string.notification_channel_id), name, IMPORTANCE_HIGH)
            channel.enableVibration(true)
            notificationManager!!.createNotificationChannel(channel)
        }
    }

    private fun getNotification(text: String): Notification {
        val intent = Intent(this, MessagingService::class.java)
        intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true)
//        val activityPendingIntent = PendingIntent.getActivity(
//            this, 0,
//            Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT
//        )
        val builder = NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
//            .setContentIntent(activityPendingIntent)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationManagerCompat.IMPORTANCE_HIGH)
            .setWhen(System.currentTimeMillis())
        setIcon(builder)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(getString(R.string.notification_channel_id)) // Channel ID
        }

        return builder.build()
    }

    private fun setIcon(notification: NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            notification.setSmallIcon(R.mipmap.ic_stat_notification)
            notification.color = resources.getColor(R.color.colorPrimary)
        } else {
            notification.setSmallIcon(R.mipmap.ic_launcher)
        }
    }

    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the InstanceID token
     * is initially generated so this is where you would retrieve the token.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.tag(PushPushGo.TAG).d("Refreshed token: $token")
        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // Instance ID token to your app server.
        if (PushPushGo.INSTANCE != null && !PushPushGo.INSTANCE?.getApiKey().isNullOrBlank()) {
            // TODO: save token in shared pref here, token registration should be invoked by user
            GlobalScope.launch { PushPushGo.INSTANCE!!.getNetwork().registerToken(token) }
        }
    }
}

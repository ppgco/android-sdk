package com.pushpushgo.sdk.fcm

import android.app.Notification
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.pushpushgo.sdk.R
import com.pushpushgo.sdk.data.PushPushNotification


object NotificationUtils {

    fun createNotification(
        context: Context,
        title: String,
        content: String,
        playSound: Boolean,
        ongoing: Boolean,
        bundle: Bundle?
    ): Notification {
//        val intent = Intent(context, PushPushGo::class.java)
//        if (bundle != null) {
//            intent.putExtras(bundle)
//        }
//        val activityPendingIntent = PendingIntent.getActivity(
//            context, 0,
//            intent, PendingIntent.FLAG_UPDATE_CURRENT
//        )
        val builder = NotificationCompat.Builder(context, context.getString(R.string.notification_channel_id))
//            .setContentIntent(activityPendingIntent)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(ongoing)
            .setPriority(Notification.PRIORITY_LOW)
            .setWhen(System.currentTimeMillis())
        setIcon(context, builder)
        if (playSound) {
            builder.setAutoCancel(true)
            builder.setDefaults(Notification.DEFAULT_ALL)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(context.getString(R.string.notification_channel_id)) // Channel ID
        }
        return builder.build()
    }
    internal fun createNotification(
        context: Context,
        notification:  PushPushNotification,
        playSound: Boolean = true,
        ongoing: Boolean
    ): Notification{

        return createNotification(
            context,
            notification.title ?: "",
            notification.body ?: "",
            playSound,
            notification.sound ?: "default",
            notification.vibrate,
            ongoing,
            notification.priority,
            notification.badge
            )
    }
    fun createNotification(
        context: Context,
        title: String,
        content: String,
        playSound: Boolean,
        sound:String,
        vibrate:Boolean,
        ongoing: Boolean,
        priority: Int,
        badge: Int
    ): Notification {

        val builder = NotificationCompat.Builder(context, context.getString(R.string.notification_channel_id))
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(ongoing)
            .setPriority(priority)
            .setWhen(System.currentTimeMillis())
        setIcon(context, builder)
        if(badge>0){
            builder.setNumber(badge)
        }
        if(vibrate){
            builder.setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))
        }
        if (playSound) {
            builder.setAutoCancel(true)
            if(sound == "default") {
                builder.setDefaults(Notification.DEFAULT_ALL)
            }else{
                val uri = Uri.parse(sound)
                builder.setSound(uri)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(context.getString(R.string.notification_channel_id)) // Channel ID
        }
        return builder.build()
    }

    private fun setIcon(context: Context, notification: NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            notification.setSmallIcon(R.mipmap.ic_stat_notification)
            notification.setColor(context.resources.getColor(R.color.colorPrimary))
        } else {
            notification.setSmallIcon(R.mipmap.ic_stat_notification)
        }
    }
}
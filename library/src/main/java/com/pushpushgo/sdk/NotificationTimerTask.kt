package com.pushpushgo.sdk

import android.app.Application
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_NONE
import com.pushpushgo.sdk.network.SharedPreferencesHelper
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*

internal class NotificationTimerTask(private val application: Application) : TimerTask() {

    private val pref = SharedPreferencesHelper(application)

    private val notificationManager = NotificationManagerCompat.from(application)

    override fun run() {
        if (areNotificationsEnabled() && pref.isSubscribed) {
            Timber.tag(PushPushGo.TAG).d("Notifications enabled")

            val subscriberId = pref.subscriberId
            if (subscriberId.isBlank()) {
                GlobalScope.launch { PushPushGo.getInstance().getNetwork().registerToken() }
            }
        } else {
            Timber.tag(PushPushGo.TAG).d("Notifications disabled")
            val subscriberId = pref.subscriberId
            if (!subscriberId.isBlank()) {
                GlobalScope.launch {
                    PushPushGo.getInstance().getNetwork().unregisterSubscriber(pref.isSubscribed)
                }
                cancel()
            }
        }
    }

    private fun areNotificationsEnabled(): Boolean {
        if (notificationManager.areNotificationsEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return notificationManager.getNotificationChannel(application.getString(R.string.notification_channel_id)).let {
                    it?.importance != IMPORTANCE_NONE
                }
            }
            return true
        }

        return false
    }
}

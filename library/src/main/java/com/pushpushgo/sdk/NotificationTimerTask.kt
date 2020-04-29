package com.pushpushgo.sdk

import android.app.Application
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*

internal class NotificationTimerTask(private val application: Application) : TimerTask() {

    private val pref = getDefaultSharedPreferences(application)

    override fun run() {
        if (NotificationManagerCompat.from(application).areNotificationsEnabled()) {
            Timber.tag(PushPushGo.TAG).d("Notifications enabled")

            val subscriberId = pref.getString(PushPushGo.SUBSCRIBER_ID, "")
            val token = pref.getString(PushPushGo.LAST_TOKEN, "")
            if (subscriberId.isNullOrBlank() && !token.isNullOrBlank()) {
                GlobalScope.launch { PushPushGo.INSTANCE?.getNetwork()?.registerToken(token) }
            }
        } else {
            Timber.tag(PushPushGo.TAG).d("Notifications disabled")
            val subscriberId = pref.getString(PushPushGo.SUBSCRIBER_ID, "")
            if (!subscriberId.isNullOrBlank() && PushPushGo.INSTANCE != null) {
                GlobalScope.launch {
                    PushPushGo.INSTANCE!!.getNetwork().unregisterSubscriber(subscriberId)
                }
                cancel()
            }
        }
    }
}

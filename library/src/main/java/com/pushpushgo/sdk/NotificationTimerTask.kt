package com.pushpushgo.sdk

import android.app.Application
import androidx.core.app.NotificationManagerCompat
import com.pushpushgo.sdk.network.SharedPreferencesHelper
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*

internal class NotificationTimerTask(private val application: Application) : TimerTask() {

    private val pref = SharedPreferencesHelper(application)

    override fun run() {
        if (NotificationManagerCompat.from(application).areNotificationsEnabled()) {
            Timber.tag(PushPushGo.TAG).d("Notifications enabled")

            val subscriberId = pref.subscriberId
            val token = pref.lastToken
            if (subscriberId.isBlank() && !token.isBlank()) {
                GlobalScope.launch { PushPushGo.INSTANCE?.getNetwork()?.registerToken(token) }
            }
        } else {
            Timber.tag(PushPushGo.TAG).d("Notifications disabled")
            val subscriberId = pref.subscriberId
            if (!subscriberId.isBlank()) {
                GlobalScope.launch {
                    PushPushGo.INSTANCE!!.getNetwork().unregisterSubscriber(subscriberId)
                }
                cancel()
            }
        }
    }
}

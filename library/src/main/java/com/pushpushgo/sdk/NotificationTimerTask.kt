package com.pushpushgo.sdk

import android.app.Application
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.pushpushgo.sdk.facade.PushPushGoFacade
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*

internal class NotificationTimerTask(private val application: Application) : TimerTask() {

    private val pref = getDefaultSharedPreferences(application)

    override fun run() {
        if (NotificationManagerCompat.from(application).areNotificationsEnabled()) {
            Timber.tag(PushPushGoFacade.TAG).d("Notifications enabled")

            val subscriberId = pref.getString(PushPushGoFacade.SUBSCRIBER_ID, "")
            val token = pref.getString(PushPushGoFacade.LAST_TOKEN, "")
            if (subscriberId.isNullOrBlank() && !token.isNullOrBlank()) {
                GlobalScope.launch { PushPushGoFacade.INSTANCE?.getNetwork()?.registerToken(token) }
            }
        } else {
            Timber.tag(PushPushGoFacade.TAG).d("Notifications disabled")
            val subscriberId = pref.getString(PushPushGoFacade.SUBSCRIBER_ID, "")
            if (!subscriberId.isNullOrBlank() && PushPushGoFacade.INSTANCE != null) {
                GlobalScope.launch {
                    PushPushGoFacade.INSTANCE!!.getNetwork().unregisterSubscriber(subscriberId)
                }
                cancel()
            }
        }
    }
}

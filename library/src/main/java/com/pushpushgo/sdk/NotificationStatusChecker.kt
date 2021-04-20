package com.pushpushgo.sdk

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_NONE
import androidx.core.content.getSystemService
import com.pushpushgo.sdk.network.SharedPreferencesHelper
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*

internal class NotificationStatusChecker private constructor(private val context: Context) : TimerTask() {

    private val pref = SharedPreferencesHelper(context)

    private val notificationManager = NotificationManagerCompat.from(context)

    private val activityManager = context.getSystemService<ActivityManager>()

    companion object {
        private const val CHECK_PERIOD = 10_000L

        fun start(context: Context) {
            Timer().scheduleAtFixedRate(NotificationStatusChecker(context), 0, CHECK_PERIOD)
        }
    }

    override fun run() {
        if (isAppOnForeground()) checkNotificationsStatus()
    }

    private fun isAppOnForeground(): Boolean {
        return activityManager?.runningAppProcesses.orEmpty().any {
            it.importance == IMPORTANCE_FOREGROUND && it.processName == context.packageName
        }
    }

    private fun checkNotificationsStatus() {
        if (areNotificationsEnabled() && pref.isSubscribed) {
            Timber.tag(PushPushGo.TAG).v("Notifications enabled")

            if (pref.subscriberId.isBlank()) {
                GlobalScope.launch {
                    Timber.tag(PushPushGo.TAG).d("Notifications enabled, but not subscribed. Registering token...")
                    PushPushGo.getInstance().getNetwork().registerToken(null)
                }
            }
        } else {
            Timber.tag(PushPushGo.TAG).v("Notifications disabled")

            if (pref.subscriberId.isNotBlank()) {
                GlobalScope.launch {
                    Timber.tag(PushPushGo.TAG).d("Notifications disabled, but subscribed. Unregistering subscriber...")
                    PushPushGo.getInstance().getNetwork().unregisterSubscriber(pref.isSubscribed)
                }
            }
        }
    }

    private fun areNotificationsEnabled(): Boolean {
        if (notificationManager.areNotificationsEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return notificationManager.getNotificationChannel(context.getString(R.string.pushpushgo_notification_default_channel_id)).let {
                    it?.importance != IMPORTANCE_NONE
                }
            }
            return true
        }

        return false
    }
}

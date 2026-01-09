package com.pushpushgo.sdk.push

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.push.areNotificationsEnabled
import com.pushpushgo.sdk.push.utils.logDebug
import com.pushpushgo.sdk.push.utils.logError
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask

internal class NotificationStatusChecker private constructor(
  private val context: Context,
) : TimerTask() {
  private val checkerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  private val errorHandler = CoroutineExceptionHandler { _, e -> logError(e) }

  private val pref = SharedPreferencesHelper(context)

  private val notificationManager = NotificationManagerCompat.from(context)

  private val activityManager = context.getSystemService<ActivityManager>()

  companion object {
    private const val CHECK_PERIOD = 10_000L

    fun start(context: Context) {
      Timer().schedule(NotificationStatusChecker(context), 0, CHECK_PERIOD)
    }
  }

  override fun run() {
    if (isAppOnForeground()) checkNotificationsStatus()
  }

  private fun isAppOnForeground(): Boolean =
    activityManager?.runningAppProcesses.orEmpty().any {
      it.importance == IMPORTANCE_FOREGROUND && it.processName == context.packageName
    }

  private fun checkNotificationsStatus() {
    if (areNotificationsEnabled(context) && pref.isSubscribed) {
      if (BuildConfig.DEBUG) logDebug("Notifications enabled")

      if (pref.subscriberId.isBlank()) {
        checkerScope.launch(errorHandler) {
          logDebug("Notifications enabled, but not subscribed. Registering token...")
          PushNotifications.Companion.getInstance().registerSubscriber()
        }
      }
    } else {
      if (BuildConfig.DEBUG) logDebug("Notifications disabled")

      if (pref.subscriberId.isNotBlank()) {
        checkerScope.launch(errorHandler) {
          logDebug("Notifications disabled, but subscribed. Unregistering subscriber...")
          PushNotifications.Companion
            .getInstance()
            .getNetwork()
            .unregisterSubscriber()
        }
      }
    }
  }
}

package com.pushpushgo.sdk.push

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.content.Context
import androidx.core.content.getSystemService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.push.areNotificationsEnabled
import com.pushpushgo.sdk.push.utils.logDebug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class NotificationStatusChecker(
  private val context: Context,
  private val sdkScope: CoroutineScope,
  private val sharedPreferencesHelper: SharedPreferencesHelper,
) {
  private val activityManager = context.getSystemService<ActivityManager>()

  companion object {
    private const val CHECK_PERIOD = 10_000L
  }

  fun start() {
    sdkScope.launch {
      while (true) {
        if (isAppOnForeground() && !isMigrating()) {
          checkNotificationsStatus()
        }

        delay(CHECK_PERIOD)
      }
    }
  }

  private fun isMigrating(): Boolean = PushNotifications.getInstance().isMigrating.get()

  private fun isAppOnForeground(): Boolean =
    activityManager?.runningAppProcesses.orEmpty().any {
      it.importance == IMPORTANCE_FOREGROUND && it.processName == context.packageName
    }

  private suspend fun checkNotificationsStatus() {
    if (areNotificationsEnabled(context) && sharedPreferencesHelper.isSubscribed) {
      if (sharedPreferencesHelper.subscriberId == null) {
        logDebug("Notifications enabled, but not subscribed. Registering token...")
        PushNotifications.getInstance().subscribeNow()
      }
    } else {
      if (sharedPreferencesHelper.subscriberId != null) {
        logDebug("Notifications disabled, but subscribed. Unregistering subscriber...")
        PushNotifications.getInstance().unsubscribeNow()
      }
    }
  }
}

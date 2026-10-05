package com.pushpushgo.sdk.push

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.content.Context
import androidx.core.content.getSystemService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.push.areNotificationsEnabled
import com.pushpushgo.sdk.push.utils.logDebug
import com.pushpushgo.sdk.push.utils.logError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the backend registration in line with the notification permission while the app is in
 * the foreground: unregisters the device when notifications get disabled and registers it again
 * once they are enabled back, as long as the user's subscription request is still in place.
 *
 * [register] and [unregister] belong to the runtime that started this checker, so a checker of a
 * released runtime never acts on its successor.
 */
internal class NotificationStatusChecker(
  private val context: Context,
  private val sdkScope: CoroutineScope,
  private val sharedPreferencesHelper: SharedPreferencesHelper,
  private val register: suspend () -> Unit,
  private val unregister: suspend () -> Unit,
) {
  private val activityManager = context.getSystemService<ActivityManager>()

  companion object {
    private const val CHECK_PERIOD = 10_000L
  }

  fun start() {
    sdkScope.launch {
      while (true) {
        if (isAppOnForeground()) {
          try {
            checkNotificationsStatus()
          } catch (exception: Exception) {
            logError("Notification status check failed", exception)
          }
        }

        delay(CHECK_PERIOD)
      }
    }
  }

  private fun isAppOnForeground(): Boolean =
    activityManager?.runningAppProcesses.orEmpty().any {
      it.importance == IMPORTANCE_FOREGROUND && it.processName == context.packageName
    }

  internal suspend fun checkNotificationsStatus() {
    val shouldBeRegistered = areNotificationsEnabled(context) && sharedPreferencesHelper.subscriptionRequested
    val isRegistered = sharedPreferencesHelper.subscriberId != null

    if (shouldBeRegistered && !isRegistered) {
      logDebug("Notifications enabled and subscription requested, but not registered. Registering token...")
      register()
    } else if (!shouldBeRegistered && isRegistered) {
      logDebug("Notifications disabled or subscription not requested, but registered. Unregistering subscriber...")
      unregister()
    }
  }
}

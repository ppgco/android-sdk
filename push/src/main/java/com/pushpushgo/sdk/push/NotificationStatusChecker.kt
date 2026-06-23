package com.pushpushgo.sdk.push

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.content.Context
import androidx.core.content.getSystemService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.push.areNotificationsEnabled
import com.pushpushgo.sdk.push.utils.getPlatformPushToken
import com.pushpushgo.sdk.push.utils.logDebug
import com.pushpushgo.sdk.push.utils.logError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class NotificationStatusChecker(
  private val context: Context,
  private val sdkScope: CoroutineScope,
  private val sharedPreferencesHelper: SharedPreferencesHelper,
) {
  private val activityManager = context.getSystemService<ActivityManager>()

  // Reconcile the push token once per SDK lifecycle (on the first foreground
  // check); re-registering on every 10s tick would hammer FCM/HMS needlessly.
  private var tokenReconciled = false

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
      } else if (!tokenReconciled) {
        reconcileToken()
      }
    } else {
      if (sharedPreferencesHelper.subscriberId != null) {
        logDebug("Notifications disabled, but subscribed. Unregistering subscriber...")
        PushNotifications.getInstance().unsubscribeNow()
      }
    }
  }

  /**
   * Compares the current platform push token with the one we last registered.
   * If it rotated while the app was offline (the FCM/HMS `onNewToken` callback
   * was missed), re-register so campaigns stop targeting a dead token.
   */
  private suspend fun reconcileToken() {
    val currentToken =
      runCatching { getPlatformPushToken(context) }
        .getOrElse {
          logError("Token reconciliation failed", it)
          return
        }?.takeIf { it.isNotBlank() } ?: return

    tokenReconciled = true

    if (currentToken != sharedPreferencesHelper.lastToken) {
      logDebug("Push token drift detected, re-registering")
      PushNotifications.getInstance().uploadManager.sendRegister(currentToken)
    }
  }
}

package com.pushpushgo.sdk.push

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.Application
import android.os.Bundle
import androidx.core.content.getSystemService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.push.areNotificationsEnabled
import com.pushpushgo.sdk.push.utils.logDebug
import com.pushpushgo.sdk.push.utils.logError
import com.pushpushgo.sdk.push.utils.logWarning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Keeps the backend registration in line with the notification permission: unregisters the device
 * when notifications get disabled and registers it again once they are enabled back, as long as
 * the user's subscription request is still in place.
 *
 * Checks run whenever an activity of the app is resumed - so a permission changed in the system
 * settings is noticed as soon as the user comes back - and every [CHECK_PERIOD] while the app is in
 * the foreground, which retries a check that failed, e.g. without network. [onChecked] runs after
 * every check, also a failed one.
 *
 * [register] and [unregister] belong to the runtime that started this checker, so a checker of a
 * released runtime never acts on its successor.
 */
internal class NotificationStatusChecker(
  private val application: Application,
  private val sdkScope: CoroutineScope,
  private val sharedPreferencesHelper: SharedPreferencesHelper,
  private val register: suspend () -> Unit,
  private val unregister: suspend () -> Unit,
  private val onChecked: () -> Unit = {},
) {
  private val activityManager = application.getSystemService<ActivityManager>()
  private val checkMutex = Mutex()

  // Guarded by checkMutex
  private var consecutiveFailures = 0

  private val activityCallbacks =
    object : Application.ActivityLifecycleCallbacks {
      override fun onActivityResumed(activity: Activity) {
        sdkScope.launch { checkNow() }
      }

      override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
      ) = Unit

      override fun onActivityStarted(activity: Activity) = Unit

      override fun onActivityPaused(activity: Activity) = Unit

      override fun onActivityStopped(activity: Activity) = Unit

      override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
      ) = Unit

      override fun onActivityDestroyed(activity: Activity) = Unit
    }

  companion object {
    private const val CHECK_PERIOD = 10_000L
  }

  fun start() {
    application.registerActivityLifecycleCallbacks(activityCallbacks)

    sdkScope.launch {
      while (true) {
        if (isAppOnForeground()) {
          checkNow()
        }

        delay(CHECK_PERIOD)
      }
    }
  }

  fun stop() {
    application.unregisterActivityLifecycleCallbacks(activityCallbacks)
  }

  /**
   * Runs a check unless one is already running. A failure is reported to the error callback once
   * per series of failed checks, so a check retried without network does not flood it.
   */
  internal suspend fun checkNow() {
    if (!checkMutex.tryLock()) return

    try {
      checkNotificationsStatus()
      consecutiveFailures = 0
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      consecutiveFailures++
      if (consecutiveFailures == 1) {
        logError("Notification status check failed", exception)
      } else {
        logWarning("Notification status check failed again ($consecutiveFailures in a row): $exception")
      }
    } finally {
      checkMutex.unlock()
      onChecked()
    }
  }

  private fun isAppOnForeground(): Boolean =
    activityManager?.runningAppProcesses.orEmpty().any {
      it.importance == IMPORTANCE_FOREGROUND && it.processName == application.packageName
    }

  internal suspend fun checkNotificationsStatus() {
    val shouldBeRegistered = areNotificationsEnabled(application) && sharedPreferencesHelper.subscriptionRequested
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

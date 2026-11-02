package com.pushpushgo.sdk.push

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.push.areNotificationsEnabled
import com.pushpushgo.sdk.push.utils.logDebug
import com.pushpushgo.sdk.push.utils.logError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Keeps the backend registration in line with the notification permission: unregisters the device
 * when notifications get disabled and registers it again once they are enabled back, as long as
 * the user's subscription request is still in place.
 *
 * A check runs when the SDK starts and whenever an activity of the app is resumed, so a permission
 * changed in the system settings is noticed as soon as the user comes back. A failed check is
 * retried on the next initialization or activity resume. [onChecked] runs after every check, also
 * a failed one.
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

  fun start() {
    application.registerActivityLifecycleCallbacks(activityCallbacks)
    sdkScope.launch { checkNow() }
  }

  fun stop() {
    application.unregisterActivityLifecycleCallbacks(activityCallbacks)
  }

  internal suspend fun checkNow() {
    try {
      checkNotificationsStatus()
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      logError("Notification status check failed", exception)
    } finally {
      onChecked()
    }
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

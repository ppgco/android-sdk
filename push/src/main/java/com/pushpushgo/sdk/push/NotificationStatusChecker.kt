package com.pushpushgo.sdk.push

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Calls [onCheck] when the SDK starts and whenever an activity of the app is resumed, so a
 * notification permission changed in the system settings is noticed as soon as the user comes back.
 */
internal class NotificationStatusChecker(
  private val application: Application,
  private val onCheck: () -> Unit,
) {
  private val activityCallbacks =
    object : Application.ActivityLifecycleCallbacks {
      override fun onActivityResumed(activity: Activity) {
        onCheck()
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
    onCheck()
  }

  fun stop() {
    application.unregisterActivityLifecycleCallbacks(activityCallbacks)
  }
}

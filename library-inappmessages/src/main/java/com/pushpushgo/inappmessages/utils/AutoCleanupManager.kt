package com.pushpushgo.inappmessages.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * Utility class that manages automatic cleanup of SDK resources
 * when the app has been in background for a specified amount of time.
 *
 * This follows the Single Responsibility Principle by extracting
 * the lifecycle management logic from the main SDK class.
 */
internal class AutoCleanupManager(
  private val application: Application,
  private val cleanupCallback: () -> Unit,
  private val backgroundTimeoutMs: Long = DEFAULT_BACKGROUND_TIMEOUT_MS,
) {
  companion object {
    // Default timeout after which we perform cleanup when app is in background (5 minutes)
    const val DEFAULT_BACKGROUND_TIMEOUT_MS = 5 * 60 * 1000L
  }

  private var isInBackground = false
  private val cleanupHandler = Handler(Looper.getMainLooper())
  private var cleanupRunnable: Runnable? = null

  /**
   * Start monitoring app lifecycle to perform automatic cleanup
   */
  fun start() {
    application.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
  }

  /**
   * Stop monitoring app lifecycle
   */
  fun stop() {
    application.unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks)
    cancelScheduledCleanup()
  }

  /**
   * Schedule cleanup after app has been in background for the specified time
   */
  private fun scheduleCleanup() {
    cancelScheduledCleanup() // Cancel any existing scheduled cleanup

    cleanupRunnable =
      Runnable {
        if (isInBackground) {
          cleanupCallback.invoke()
        }
      }

    cleanupHandler.postDelayed(cleanupRunnable!!, backgroundTimeoutMs)
  }

  /**
   * Cancel any scheduled cleanup
   */
  private fun cancelScheduledCleanup() {
    cleanupRunnable?.let {
      cleanupHandler.removeCallbacks(it)
    }
  }

  /**
   * Activity lifecycle callbacks to detect app background state
   */
  private val activityLifecycleCallbacks =
    object : Application.ActivityLifecycleCallbacks {
      private var activeActivities = 0

      override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
      ) {}

      override fun onActivityStarted(activity: Activity) {
        if (activeActivities == 0) {
          isInBackground = false
          cancelScheduledCleanup()
        }
        activeActivities++
      }

      override fun onActivityResumed(activity: Activity) {}

      override fun onActivityPaused(activity: Activity) {}

      override fun onActivityStopped(activity: Activity) {
        activeActivities--
        if (activeActivities == 0) {
          // App is going to background
          isInBackground = true
          scheduleCleanup()
        }
      }

      override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
      ) {}

      override fun onActivityDestroyed(activity: Activity) {}
    }
}

package com.pushpushgo.inappmessages.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Utility class that manages automatic cleanup of SDK resources
 * when the app has been in background for a specified amount of time.
 *
 * This follows the Single Responsibility Principle by extracting
 * the lifecycle management logic from the main SDK class.
 */
class AutoCleanupManager(
  private val application: Application,
  private val cleanupCallback: () -> Unit,
  private val backgroundTimeoutMs: Long = DEFAULT_BACKGROUND_TIMEOUT_MS,
) {
  companion object {
    private const val TAG = "AutoCleanupManager"

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
    Log.d(TAG, "AutoCleanupManager started")
  }

  /**
   * Stop monitoring app lifecycle
   */
  fun stop() {
    application.unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks)
    cancelScheduledCleanup()
    Log.d(TAG, "AutoCleanupManager stopped")
  }

  /**
   * Schedule cleanup after app has been in background for the specified time
   */
  private fun scheduleCleanup() {
    Log.d(TAG, "Scheduling background cleanup in ${backgroundTimeoutMs / 1000} seconds")
    cancelScheduledCleanup() // Cancel any existing scheduled cleanup

    cleanupRunnable =
      Runnable {
        if (isInBackground) {
          Log.d(TAG, "Performing automatic background cleanup")
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
      Log.d(TAG, "Canceled scheduled background cleanup")
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
          // App is coming to foreground
          isInBackground = false
          cancelScheduledCleanup()
          Log.d(TAG, "App returned to foreground")
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
          Log.d(TAG, "App moved to background")
        }
      }

      override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
      ) {}

      override fun onActivityDestroyed(activity: Activity) {}
    }
}

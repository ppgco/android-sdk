package com.pushpushgo.sdk.inapp.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.pushpushgo.sdk.inapp.InAppMessages
import com.pushpushgo.sdk.inapp.manager.InAppMessageManager
import com.pushpushgo.sdk.inapp.model.InAppMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

internal class InAppUIController(
  private val application: Application,
  private val manager: InAppMessageManager,
  private val displayer: InAppMessageDisplayer,
  private val debug: Boolean = false,
) : Application.ActivityLifecycleCallbacks,
  CoroutineScope {
  private val job = SupervisorJob()
  override val coroutineContext = Dispatchers.Main + job

  private var currentActivity: WeakReference<Activity?> = WeakReference(null)
  private var messageSubscription: Job? = null

  /**
   * Starts displaying messages. Pass the [activity] already on screen when the controller replaces
   * another one (after a project switch) - otherwise messages wait for the next resumed activity.
   */
  fun start(activity: Activity? = null) {
    if (debug) {
      Log.d(InAppMessages.TAG, "[UIController] Starting")
    }
    if (activity != null && !activity.isFinishing) {
      currentActivity = WeakReference(activity)
    }
    application.registerActivityLifecycleCallbacks(this)
    observeMessages()
  }

  /** Stops displaying messages and hides the one on screen. */
  fun stop() {
    if (debug) {
      Log.d(InAppMessages.TAG, "[UIController] Stopping")
    }
    application.unregisterActivityLifecycleCallbacks(this)
    job.cancel()

    // Dialogs can only be dismissed on the main thread
    if (Looper.myLooper() == Looper.getMainLooper()) {
      displayer.release()
    } else {
      Handler(Looper.getMainLooper()).post { displayer.release() }
    }
  }

  fun getCurrentActivity(): Activity? = currentActivity.get()

  private fun observeMessages() {
    if (debug) {
      Log.d(InAppMessages.TAG, "[UIController] Starting to observe messages flow")
    }
    messageSubscription?.cancel()
    messageSubscription =
      manager.messagesFlow
        .distinctUntilChanged()
        .onEach { messages ->
          val activity = currentActivity.get()
          if (activity == null || activity.isFinishing) {
            if (debug) {
              Log.d(InAppMessages.TAG, "[UIController] No current activity available, skipping message display")
            }
            return@onEach
          }

          if (messages.isNotEmpty()) {
            if (debug) {
              Log.d(
                InAppMessages.TAG,
                "[UIController] Displaying message from flow: [${messages.first().id}] (${messages.size} total messages available)",
              )
            }
            val highestPriorityMessage = messages.first()
            displayer.showMessage(activity, highestPriorityMessage)
          } else {
            if (debug) {
              Log.d(InAppMessages.TAG, "[UIController] No messages to display")
            }
            displayer.cancelPendingMessages(isActivityPaused = false)
          }
        }.launchIn(this)
  }

  override fun onActivityResumed(activity: Activity) {
    if (debug) {
      Log.d(InAppMessages.TAG, "[UIController] Activity resumed: ${activity.javaClass.simpleName}")
    }

    currentActivity = WeakReference(activity)

    launch {
      manager.refreshActiveMessages(manager.getRoute())
      // Force display of available messages after activity resume
      // This handles the case where distinctUntilChanged() blocks emission
      // of the same message list after permission for push notifications changes
      val currentMessages = manager.getActiveMessages()
      if (currentMessages.isNotEmpty()) {
        val highestPriorityMessage = currentMessages.first()
        displayer.showMessage(activity, highestPriorityMessage)
      }
    }
  }

  override fun onActivityPaused(activity: Activity) {
    if (currentActivity.get() == activity) {
      if (debug) {
        Log.d(InAppMessages.TAG, "[UIController] Activity paused, cancelling pending messages")
      }

      currentActivity.clear()
    }

    displayer.cancelPendingMessages(isActivityPaused = true)
  }

  override fun onActivityCreated(
    activity: Activity,
    savedInstanceState: Bundle?,
  ) = Unit

  override fun onActivityStarted(activity: Activity) = Unit

  override fun onActivityStopped(activity: Activity) = Unit

  override fun onActivitySaveInstanceState(
    activity: Activity,
    outState: Bundle,
  ) = Unit

  override fun onActivityDestroyed(activity: Activity) = displayer.onActivityDestroyed(activity)

  fun displayCustomMessage(message: InAppMessage) {
    launch {
      val activity = currentActivity.get()

      if (activity == null || activity.isFinishing) {
        return@launch
      }

      displayer.showMessage(activity, message)
    }
  }
}

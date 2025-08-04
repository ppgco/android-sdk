package com.pushpushgo.inappmessages.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.pushpushgo.inappmessages.manager.InAppMessageManager
import com.pushpushgo.inappmessages.model.InAppMessage
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
) : Application.ActivityLifecycleCallbacks,
  CoroutineScope {
  private val tag = "InAppUIController"

  private val job = SupervisorJob()
  override val coroutineContext = Dispatchers.Main + job

  private var currentActivity: WeakReference<Activity?> = WeakReference(null)
  private var messageSubscription: Job? = null

  fun start() {
    Log.d(tag, "Starting InAppUIController")
    application.registerActivityLifecycleCallbacks(this)
    observeMessages()
  }

  fun stop() {
    Log.d(tag, "Stopping InAppUIController")
    application.unregisterActivityLifecycleCallbacks(this)
    job.cancel()
    messageSubscription?.cancel()
  }

  private fun observeMessages() {
    messageSubscription?.cancel()
    messageSubscription =
      manager.messagesFlow
        .distinctUntilChanged()
        .onEach { messages ->
          val activity = currentActivity.get()
          if (activity == null || activity.isFinishing) {
            Log.d(tag, "No active activity, skipping message display")
            return@onEach
          }

          if (messages.isNotEmpty()) {
            val highestPriorityMessage = messages.first()
            Log.d(tag, "Displaying message: ${highestPriorityMessage.id}")
            displayer.showMessage(activity, highestPriorityMessage)
          } else {
            Log.d(tag, "No messages to display. Attempting to cancel pending messages in displayer: $displayer")
            displayer.cancelPendingMessages(isActivityPaused = false)
          }
        }.launchIn(this)
  }

  override fun onActivityResumed(activity: Activity) {
    currentActivity = WeakReference(activity)
    Log.d(tag, "Activity resumed: ${activity.localClassName}")
    launch {
      manager.refreshActiveMessages()

      // Force display of available messages after activity resume
      // This handles the case where distinctUntilChanged() blocks emission
      // of the same message list after permission for push notifications changes
      val currentMessages = manager.getActiveMessages()
      if (currentMessages.isNotEmpty()) {
        Log.d(tag, "Force displaying messages after activity resume: ${currentMessages.size} available")
        val highestPriorityMessage = currentMessages.first()
        displayer.showMessage(activity, highestPriorityMessage)
      } else {
        Log.d(tag, "No messages available for force display after activity resume")
      }
    }
  }

  override fun onActivityPaused(activity: Activity) {
    if (currentActivity.get() == activity) {
      currentActivity.clear()
    }
    Log.d(tag, "Activity paused: ${activity.localClassName}. Attempting to cancel pending messages in displayer: $displayer")
    displayer.cancelPendingMessages(isActivityPaused = true)
    Log.d(tag, "Activity paused: ${activity.localClassName}")
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

  override fun onActivityDestroyed(activity: Activity) = Unit

  fun displayCustomMessage(message: InAppMessage) {
    launch {
      val activity = currentActivity.get()
      if (activity == null || activity.isFinishing) {
        Log.d(tag, "No active activity, skipping custom message display for ${message.id}")
        return@launch
      }
      Log.d(tag, "Displaying custom message: ${message.id}")
      displayer.showMessage(activity, message)
    }
  }
}

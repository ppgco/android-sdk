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
  private val debug: Boolean = false,
) : Application.ActivityLifecycleCallbacks,
  CoroutineScope {
  private val tag = "InAppUIController"

  private val job = SupervisorJob()
  override val coroutineContext = Dispatchers.Main + job

  private var currentActivity: WeakReference<Activity?> = WeakReference(null)
  private var messageSubscription: Job? = null

  fun start() {
    if (debug) {
      Log.d(tag, "Starting InApp UI Controller")
    }
    application.registerActivityLifecycleCallbacks(this)
    observeMessages()
  }

  fun stop() {
    application.unregisterActivityLifecycleCallbacks(this)
    job.cancel()
    messageSubscription?.cancel()
  }

  private fun observeMessages() {
    if (debug) {
      Log.d(tag, "Starting to observe messages flow")
    }
    messageSubscription?.cancel()
    messageSubscription =
      manager.messagesFlow
        .distinctUntilChanged()
        .onEach { messages ->
          val activity = currentActivity.get()
          if (activity == null || activity.isFinishing) {
            if (debug) {
              Log.d(tag, "No current activity available, skipping message display")
            }
            return@onEach
          }

          if (messages.isNotEmpty()) {
            if (debug) {
              Log.d(tag, "Displaying message from flow: [${messages.first().id}] (${messages.size} total messages available)")
            }
            val highestPriorityMessage = messages.first()
            displayer.showMessage(activity, highestPriorityMessage)
          } else {
            if (debug) {
              Log.d(tag, "No messages to display")
            }
            displayer.cancelPendingMessages(isActivityPaused = false)
          }
        }.launchIn(this)
  }

  override fun onActivityResumed(activity: Activity) {
    if (debug) {
      Log.d(tag, "Activity resumed: ${activity.javaClass.simpleName}")
    }
    currentActivity = WeakReference(activity)

    launch {
      manager.refreshActiveMessages()
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
        Log.d(tag, "Activity paused, cancelling pending messages")
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

  override fun onActivityDestroyed(activity: Activity) = Unit

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

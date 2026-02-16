package com.pushpushgo.sdk.inapp.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pushpushgo.sdk.core.api.PushSubscriptionProvider
import com.pushpushgo.sdk.inapp.InAppMessages
import com.pushpushgo.sdk.inapp.R
import com.pushpushgo.sdk.inapp.model.AnimationType
import com.pushpushgo.sdk.inapp.model.InAppActionType
import com.pushpushgo.sdk.inapp.model.InAppMessage
import com.pushpushgo.sdk.inapp.model.InAppMessageAction
import com.pushpushgo.sdk.inapp.model.ShowAgainType
import com.pushpushgo.sdk.inapp.persistence.InAppMessagePersistence
import com.pushpushgo.sdk.inapp.ui.composables.templates.InAppMessageDefaultTemplate
import com.pushpushgo.sdk.inapp.ui.composables.templates.TemplateBannerMessage
import com.pushpushgo.sdk.inapp.ui.composables.templates.TemplateReviewForDiscount
import com.pushpushgo.sdk.inapp.ui.composables.templates.TemplateRichMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext

internal class InAppMessageDisplayerImpl(
  private val persistence: InAppMessagePersistence? = null,
  private val debug: Boolean = false,
  private val onMessageDismissed: () -> Unit,
  private val onMessageEvent: (eventType: String, message: InAppMessage, ctaIndex: Int?) -> Unit = { _, _, _ -> },
  private val pushSubscriptionProvider: PushSubscriptionProvider? = null,
  private val customCodeHandler: CustomCodeHandler? = null,
) : InAppMessageDisplayer,
  CoroutineScope {
  // Coroutine context and job for managing message display jobs
  private val job = SupervisorJob()
  override val coroutineContext: CoroutineContext
    get() = Dispatchers.Main + job

  // Store current UI elements
  private var currentDialog: Dialog? = null

  // Flag to prevent re-entrant calls to dismissMessage
  private var isDismissing = false

  // Map to store pending delayed message jobs
  private val pendingMessageJobs = mutableMapOf<String, Pair<InAppMessage, Job>>()

  override fun showMessage(
    activity: Activity,
    message: InAppMessage,
  ) {
    // Cancel all other pending jobs before scheduling a new one.
    // This ensures only one message is either pending or being displayed at a time.
    cancelPendingMessages(isActivityPaused = false)

    val delaySec = message.settings.showAfterDelay
    if (delaySec > 0) {
      val delayMs = delaySec * 1000L
      if (debug) {
        Log.d(InAppMessages.TAG, "[Displayer] Scheduling message [${message.id}] with ${delayMs}ms delay")
      }
      val activityRef = WeakReference(activity)

      val newJob =
        launch {
          try {
            delay(delayMs)

            val currentActivity = activityRef.get()
            if (currentActivity == null || currentActivity.isFinishing) {
              return@launch
            }

            if (!isActive) { // Check if job was cancelled during delay
              return@launch
            }

            withContext(Dispatchers.Main) {
              // Ensure UI ops on Main
              if (shouldBeDisplayed(message)) {
                showMessageByTemplate(currentActivity, message)
              }
            }
          } catch (t: Throwable) {
            if (t is CancellationException) {
              if (debug) {
                Log.d(InAppMessages.TAG, "[Displayer] Message [${message.id}] display cancelled")
              }
              throw t
            }
            Log.e(InAppMessages.TAG, "[Displayer] Failed to display message ${message.id}", t)
          } finally {
            // Always remove the job from the map when it's done.
            // This check prevents a race condition where a new job for the same ID is added
            // before the old one's finally block executes.
            if (pendingMessageJobs[message.id]?.second == this.coroutineContext[Job]) {
              pendingMessageJobs.remove(message.id)
            }
          }
        }
      pendingMessageJobs[message.id] = message to newJob
    } else {
      if (debug) {
        Log.d(InAppMessages.TAG, "[Displayer] Showing message [${message.id}] immediately")
      }
      launch {
        if (shouldBeDisplayed(message)) {
          showMessageByTemplate(activity, message)
        }
      }
    }
  }

  private suspend fun showMessageByTemplate(
    activity: Activity,
    message: InAppMessage,
  ) {
    // Dismiss any currently visible message before showing a new one
    hideMessage()

    val dialogStyle =
      when (message.template) {
        "WEBSITE_TO_HOME_SCREEN",
        "PAYWALL_PUBLISH",
        -> {
          R.style.InAppMessageDialog_Modal
        }

        "EXIT_INTENT_ECOMM",
        "PUSH_NOTIFICATION_OPT_IN",
        "EXIT_INTENT_TRAVEL",
        "UNBLOCK_NOTIFICATIONS",
        "LOW_STOCK",
        "REVIEW_FOR_DISCOUNT",
        -> {
          R.style.InAppMessageDialog_Banner
        }

        else -> {
          Log.w(InAppMessages.TAG, "[Displayer] Unsupported template: ${message.template}, no container style defined.")
          null
        }
      }

    dialogStyle?.let {
      displayMessageInContainer(activity, message, it)
    }
  }

  override fun cancelPendingMessages(isActivityPaused: Boolean) {
    if (pendingMessageJobs.isEmpty()) {
      return
    }
    if (debug) {
      Log.d(
        InAppMessages.TAG,
        "[Displayer] Cancelling ${pendingMessageJobs.size} pending message jobs (activity paused: $isActivityPaused)",
      )
    }

    // Create a copy of the values to avoid ConcurrentModificationException
    val jobsToCancel = ArrayList(pendingMessageJobs.values)
    jobsToCancel.forEach { (_, job) ->
      job.cancel(CancellationException("Pending message cancelled by new event"))
    }
    pendingMessageJobs.clear()
  }

  private fun hideMessage() {
    currentDialog?.let {
      if (it.isShowing) {
        it.dismiss()
      }
    }
    currentDialog = null
  }

  override fun dismissMessage(message: InAppMessage) {
    if (debug) {
      Log.d(InAppMessages.TAG, "[Displayer] Dismissing message [${message.id}]")
    }
    dismissMessageInternal(message, sendCloseEvent = true)
  }

  /**
   * Dismisses the message without sending the "close" event.
   * Used when the message is dismissed as a result of a button action,
   * where we want to send "cta" event instead of "close".
   */
  private fun dismissMessageSilently(message: InAppMessage) {
    dismissMessageInternal(message, sendCloseEvent = false)
  }

  /**
   * Internal method that handles the actual dismissal logic.
   *
   * @param message The message to dismiss
   * @param sendCloseEvent Whether to send the "close" event
   */
  private fun dismissMessageInternal(
    message: InAppMessage,
    sendCloseEvent: Boolean,
  ) {
    if (isDismissing) return // Prevent re-entrant calls

    try {
      isDismissing = true

      launch(Dispatchers.IO) {
        persistence?.markMessageDismissed(message.id)
      }
      hideMessage()
      onMessageDismissed()

      if (sendCloseEvent) {
        onMessageEvent("close", message, null)
      }
    } finally {
      // Allow the next dismissal call after this one has fully completed
      isDismissing = false
    }
  }

  private suspend fun displayMessageInContainer(
    activity: Activity,
    message: InAppMessage,
    dialogStyleResId: Int,
  ) {
    if (shouldBeDisplayed(message).not()) return

    // Ensure UI operations are on the main thread
    withContext(Dispatchers.Main) {
      currentDialog?.dismiss()

      val composeView = createComposeView(activity, message)
      val dialog =
        Dialog(activity, dialogStyleResId).apply {
          setContentView(composeView)

          val isBanner = dialogStyleResId == R.style.InAppMessageDialog_Banner

          setCancelable(message.dismissible)
          setCanceledOnTouchOutside(message.dismissible)

          if (message.style.animationType == AnimationType.APPEAR) {
            window?.attributes?.windowAnimations = R.style.FadeInAnimation
          }

          // Handle overlay property - set window background explicitly based on overlay setting
          window?.setBackgroundDrawable(
            Color.TRANSPARENT
              .toDrawable(),
          )

          // Set background dim (overlay) based on message style setting
          window?.setDimAmount(if (message.style.overlay) 0.5f else 0f)

          // For banner style messages like REVIEW_FOR_DISCOUNT, use wrap content height
          val layoutHeight =
            if (isBanner) {
              ViewGroup.LayoutParams.WRAP_CONTENT
            } else {
              ViewGroup.LayoutParams.MATCH_PARENT
            }

          // Get dialog window and set layout params
          window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, layoutHeight)

            if (isBanner) {
              val windowAttributes = attributes

              windowAttributes.gravity =
                when {
                  message.layout.placement
                    .toString()
                    .startsWith("TOP") -> Gravity.TOP or Gravity.CENTER_HORIZONTAL

                  message.layout.placement
                    .toString()
                    .startsWith("BOTTOM") -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL

                  else -> Gravity.CENTER
                }

              attributes = windowAttributes
            }
          }
          setOnDismissListener { if (currentDialog == this) dismissMessage(message) }
        }
      dialog.show()
      currentDialog = dialog
      // Fire show event after dialog is visible
      onMessageEvent("show", message, null)
    }
  }

  private suspend fun shouldBeDisplayed(message: InAppMessage): Boolean {
    val isDismissed =
      withContext(Dispatchers.IO) {
        persistence?.isMessageDismissed(message.id)
      }
    return !(isDismissed == true && message.settings.showAgain != ShowAgainType.AFTER_TIME)
  }

  private fun createComposeView(
    activity: Activity,
    message: InAppMessage,
  ): ComposeView =
    ComposeView(activity).apply {
      // Set the ViewTreeLifecycleOwner and SavedStateRegistryOwner for the ComposeView.
      // This is crucial for Jetpack Compose to work correctly in a Dialog or any view
      // that is not directly part of the Activity's main content view, as it allows
      // the composable to observe LiveData, use ViewModels, and save instance state.
      setViewTreeLifecycleOwner(activity as? LifecycleOwner)
      setViewTreeSavedStateRegistryOwner(activity as? SavedStateRegistryOwner)

      layoutParams =
        FrameLayout
          .LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
          ).apply {
            gravity = Gravity.CENTER
          }
      setContent {
        val onAction = { action: InAppMessageAction ->
          // Find CTA index (1-based) if this is a CTA
          val ctaIndex =
            message.actions
              .indexOf(action)
              .takeIf { it >= 0 }
              ?.plus(1)
          onMessageEvent("cta", message, ctaIndex)
          handleAction(activity, action)
          // Every action click should dismiss the message silently (without close event)
          dismissMessageSilently(message)
        }

        when (message.template) {
          "PAYWALL_PUBLISH", "WEBSITE_TO_HOME_SCREEN" -> {
            TemplateRichMessage(
              message = message,
              onDismiss = { dismissMessage(message) },
              onAction = onAction,
            )
          }

          "EXIT_INTENT_ECOMM", "PUSH_NOTIFICATION_OPT_IN", "EXIT_INTENT_TRAVEL", "UNBLOCK_NOTIFICATIONS", "LOW_STOCK" -> {
            TemplateBannerMessage(
              message = message,
              onDismiss = { dismissMessage(message) },
              onAction = onAction,
            )
          }

          "REVIEW_FOR_DISCOUNT" -> {
            TemplateReviewForDiscount(
              message = message,
              onDismiss = { dismissMessage(message) },
              onAction = onAction,
            )
          }

          else -> {
            // Fallback to a default view
            InAppMessageDefaultTemplate(
              message = message,
              onDismiss = { dismissMessage(message) },
              onAction = onAction,
            )
          }
        }
      }
    }

  private fun handleAction(
    context: Context,
    action: InAppMessageAction,
  ) {
    try {
      when (action.actionType) {
        InAppActionType.REDIRECT -> {
          action.url?.takeIf { it.isNotEmpty() }?.let {
            context.startActivity(
              Intent(Intent.ACTION_VIEW, it.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              },
            )
          } ?: Log.e(InAppMessages.TAG, "[Displayer] URL is null or empty in REDIRECT action")
        }

        InAppActionType.JS -> {
          action.call?.takeIf { it.isNotEmpty() }?.let { code ->
            customCodeHandler?.handle(code)
          } ?: Log.e(InAppMessages.TAG, "[Displayer] Custom code is null or empty")
        }

        InAppActionType.SUBSCRIBE -> {
          launch {
            if (pushSubscriptionProvider == null) {
              Log.e(InAppMessages.TAG, "[Displayer] No PushSubscriptionProvider configured - cannot subscribe to notifications")
              return@launch
            }

            if (pushSubscriptionProvider.isSubscribed()) {
              Log.i(InAppMessages.TAG, "[Displayer] Already subscribed to notifications, skipping")
              return@launch
            }

            try {
              pushSubscriptionProvider.subscribe()
            } catch (e: Exception) {
              Log.e(InAppMessages.TAG, "[Displayer] Error subscribing to notifications", e)
            }

            if (pushSubscriptionProvider.isSubscribed()) {
              Toast.makeText(context, "Successfully subscribed to notifications!", Toast.LENGTH_SHORT).show()
            } else {
              Toast.makeText(context, "Subscription failed. Enable notifications in settings.", Toast.LENGTH_LONG).show()
            }
          }
        }

        InAppActionType.CLOSE -> {
          // The message is dismissed by the caller of this function,
          // so no specific action is needed here for CLOSE.
        }
      }
    } catch (e: Exception) {
      Log.e(InAppMessages.TAG, "[Displayer] Failed to handle action", e)
      Toast.makeText(context, "Error performing action", Toast.LENGTH_SHORT).show()
    }
  }
}

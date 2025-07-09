package com.pushpushgo.inappmessages.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.core.net.toUri
import com.pushpushgo.inappmessages.R
import com.pushpushgo.inappmessages.model.InAppMessage
import com.pushpushgo.inappmessages.model.InAppMessageAction
import com.pushpushgo.inappmessages.model.InAppActionType
import com.pushpushgo.inappmessages.model.ShowAgainType
import com.pushpushgo.inappmessages.persistence.InAppMessagePersistence
import com.pushpushgo.inappmessages.ui.composables.InAppMessageDefaultTemplate
import com.pushpushgo.inappmessages.ui.composables.TemplateBannerMessage
import com.pushpushgo.inappmessages.ui.composables.TemplateReviewForDiscount
import com.pushpushgo.inappmessages.ui.composables.TemplateRichMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pushpushgo.inappmessages.model.AnimationType
import kotlinx.coroutines.isActive
import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext
import androidx.core.graphics.drawable.toDrawable
import com.pushpushgo.inappmessages.utils.DefaultPushNotificationSubscriber
import com.pushpushgo.inappmessages.utils.PushNotificationSubscriber

internal class InAppMessageDisplayerImpl(
    private val persistence: InAppMessagePersistence? = null,
    private val onMessageDismissed: () -> Unit,
    private val onMessageEvent: (eventType: String, message: InAppMessage, ctaIndex: Int?) -> Unit = { _, _, _ -> },
    private var onJsAction: ((jsCall: String) -> Unit)? = null,
    private var subscriptionHandler: PushNotificationSubscriber = DefaultPushNotificationSubscriber()
) : InAppMessageDisplayer, CoroutineScope {

    private val tag = "InAppMsgDisplayer"

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

    override fun showMessage(activity: Activity, message: InAppMessage) {
        // Cancel all other pending jobs before scheduling a new one.
        // This ensures only one message is either pending or being displayed at a time.
        cancelPendingMessages(isActivityPaused = false)

        val delaySec = message.settings.showAfterDelay
        if (delaySec > 0) {
            val delayMs = delaySec * 1000L
            Log.d(
                tag,
                "Scheduling message ${message.id} (delay: ${delayMs}ms) for activity: ${activity.localClassName}"
            )
            val activityRef = WeakReference(activity)

            val newJob = launch { // This is a CoroutineScope.launch
                try {
                    Log.d(
                        tag,
                        "Job for ${message.id} [${this.coroutineContext[Job]}]: Starting delay of $delayMs ms."
                    )
                    delay(delayMs)
                    Log.d(
                        tag,
                        "Job for ${message.id} [${this.coroutineContext[Job]}]: Delay finished."
                    )

                    val currentActivity = activityRef.get()
                    if (currentActivity == null || currentActivity.isFinishing) {
                        Log.d(
                            tag,
                            "Job for ${message.id} [${this.coroutineContext[Job]}]: Activity ${activity.localClassName} no longer available or finishing."
                        )
                        return@launch
                    }

                    if (!isActive) { // Check if job was cancelled during delay
                        Log.d(
                            tag,
                            "Job for ${message.id} [${this.coroutineContext[Job]}]: Job was cancelled during delay (isActive=false)."
                        )
                        return@launch
                    }

                    Log.d(
                        tag,
                        "Job for ${message.id} [${this.coroutineContext[Job]}]: Showing message on activity ${currentActivity.localClassName}."
                    )
                    withContext(Dispatchers.Main) { // Ensure UI ops on Main
                        if (shouldBeDisplayed(message)) {
                            showMessageByTemplate(currentActivity, message)
                        }
                    }
                } catch (e: CancellationException) {
                    Log.d(
                        tag,
                        "Job for ${message.id} [${this.coroutineContext[Job]}] was cancelled: ${e.message}"
                    )
                } catch (t: Throwable) {
                    Log.e(tag, "Job for ${message.id} [${this.coroutineContext[Job]}] failed", t)
                } finally {
                    // Always remove the job from the map when it's done.
                    // This check prevents a race condition where a new job for the same ID is added
                    // before the old one's finally block executes.
                    if (pendingMessageJobs[message.id]?.second == this.coroutineContext[Job]) {
                        pendingMessageJobs.remove(message.id)
                    }
                }
            }
            Log.d(tag, "showMessage: Stored new job for ${message.id}. Job: $newJob")
            pendingMessageJobs[message.id] = message to newJob
        } else {
            launch {
                if (shouldBeDisplayed(message)) {
                    showMessageByTemplate(activity, message)
                }
            }
        }
    }

    private suspend fun showMessageByTemplate(activity: Activity, message: InAppMessage) {
        // Dismiss any currently visible message before showing a new one
        hideMessage()

        val dialogStyle = when (message.template) {
            "WEBSITE_TO_HOME_SCREEN", "PAYWALL_PUBLISH" -> R.style.InAppMessageDialog_Modal
            "EXIT_INTENT_ECOMM", "PUSH_NOTIFICATION_OPT_IN", "EXIT_INTENT_TRAVEL", "UNBLOCK_NOTIFICATIONS", "LOW_STOCK", "REVIEW_FOR_DISCOUNT" -> R.style.InAppMessageDialog_Banner
            else -> {
                Log.w(tag, "Unsupported template: ${message.template}, no container style defined.")
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
        Log.d(tag, "Cancelling all ${pendingMessageJobs.size} pending message jobs.")

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
        if (isDismissing) return // Prevent re-entrant calls

        try {
            isDismissing = true

            Log.d(tag, "dismissMessage: Marking message ${message.id} as dismissed.")
            launch(Dispatchers.IO) {
                persistence?.markMessageDismissed(message.id)
            }
            hideMessage()
            onMessageDismissed()
            onMessageEvent("close", message, null)
        } finally {
            // Allow the next dismissal call after this one has fully completed
            isDismissing = false
        }
    }

    private suspend fun displayMessageInContainer(
        activity: Activity, message: InAppMessage, dialogStyleResId: Int
    ) {
        if (shouldBeDisplayed(message).not()) return

        // Ensure UI operations are on the main thread
        withContext(Dispatchers.Main) {
            currentDialog?.dismiss()

            val composeView = createComposeView(activity, message)
            val dialog = Dialog(activity, dialogStyleResId).apply {
                setContentView(composeView)
                setCancelable(message.dismissible)
                if (message.style.animationType == AnimationType.APPEAR) {
                    window?.attributes?.windowAnimations = R.style.FadeInAnimation
                }
                window?.setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
                // For banner style messages like REVIEW_FOR_DISCOUNT, use wrap content height
                val layoutHeight = if (dialogStyleResId == R.style.InAppMessageDialog_Banner) {
                    ViewGroup.LayoutParams.WRAP_CONTENT
                } else {
                    ViewGroup.LayoutParams.MATCH_PARENT
                }

                window?.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT, layoutHeight
                )
                setOnDismissListener { if (currentDialog == this) dismissMessage(message) }
            }
            dialog.show()
            currentDialog = dialog
            // Fire show event after dialog is visible
            onMessageEvent("show", message, null)
        }
    }

    private suspend fun shouldBeDisplayed(message: InAppMessage): Boolean {
        val isDismissed = withContext(Dispatchers.IO) {
            persistence?.isMessageDismissed(message.id)
        }
        if (isDismissed == true && message.settings.showAgain != ShowAgainType.AFTER_TIME) {
            Log.d(tag, "Message ${message.id} is already dismissed and not set to show again.")
            return false
        }
        return true
    }

    private fun createComposeView(activity: Activity, message: InAppMessage): ComposeView {
        return ComposeView(activity).apply {
            // Set the ViewTreeLifecycleOwner and SavedStateRegistryOwner for the ComposeView.
            // This is crucial for Jetpack Compose to work correctly in a Dialog or any view
            // that is not directly part of the Activity's main content view, as it allows
            // the composable to observe LiveData, use ViewModels, and save instance state.
            setViewTreeLifecycleOwner(activity as? androidx.lifecycle.LifecycleOwner)
            setViewTreeSavedStateRegistryOwner(activity as? androidx.savedstate.SavedStateRegistryOwner)

            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
            }
            setContent {
                val onAction = { action: InAppMessageAction ->
                    // Find CTA index (1-based) if this is a CTA
                    val ctaIndex = message.actions.indexOf(action).takeIf { it >= 0 }?.plus(1)
                    onMessageEvent("cta", message, ctaIndex)
                    handleAction(activity, action)
                    // Every action click should dismiss the message to prevent multiple actions events
                    dismissMessage(message)
                }

                when (message.template) {
                    "PAYWALL_PUBLISH", "WEBSITE_TO_HOME_SCREEN" -> {
                        TemplateRichMessage(
                            message = message,
                            onDismiss = { dismissMessage(message) },
                            onAction = onAction
                        )
                    }

                    "EXIT_INTENT_ECOMM", "PUSH_NOTIFICATION_OPT_IN", "EXIT_INTENT_TRAVEL", "UNBLOCK_NOTIFICATIONS", "LOW_STOCK" -> {
                        TemplateBannerMessage(
                            message = message,
                            onDismiss = { dismissMessage(message) },
                            onAction = onAction
                        )
                    }

                    "REVIEW_FOR_DISCOUNT" -> {
                        TemplateReviewForDiscount(
                            message = message,
                            onDismiss = { dismissMessage(message) },
                            onAction = onAction
                        )
                    }

                    else -> {
                        // Fallback to a default view
                        InAppMessageDefaultTemplate(
                            message = message,
                            onDismiss = { dismissMessage(message) },
                            onAction = onAction
                        )
                    }
                }
            }
        }
    }

    /**
     * Sets a handler for code from actions.
     * This method allows updating the code action handler after initialization.
     *
     * @param handler Function that takes button action code string and processes it
     */
    internal fun setJsActionHandler(handler: (jsCall: String) -> Unit) {
        this.onJsAction = handler
        Log.d(tag, "JS action handler updated")
    }

    /**
     * Sets a handler for subscription requests.
     * This will be called when a SUBSCRIBE action button is clicked.
     *
     * @param handler The PushNotificationSubscriber implementation
     */
    internal fun setSubscriptionHandler(handler: PushNotificationSubscriber) {
        this.subscriptionHandler = handler
        Log.d(tag, "Subscription handler updated")
    }

    private fun handleAction(context: Context, action: InAppMessageAction) {
        try {
            when (action.actionType) {
                InAppActionType.REDIRECT -> {
                    action.url?.takeIf { it.isNotEmpty() }?.let {
                        context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri()).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    } ?: Log.e(tag, "URL is null or empty in REDIRECT action")
                }

                InAppActionType.JS -> {
                    action.call?.takeIf { it.isNotEmpty() }?.let { jsCall ->
                        Log.d(tag, "Processing JS action with call: $jsCall")
                        onJsAction?.invoke(jsCall) ?: run {
                            Log.w(tag, "No JS action handler provided for call: $jsCall")
                        }
                    } ?: Log.e(tag, "JS call value is null or empty")
                }

                InAppActionType.SUBSCRIBE -> {
                    Log.d(tag, "Processing SUBSCRIBE action request")
                    val success = try {
                        subscriptionHandler.requestSubscription(context)
                    } catch (e: Exception) {
                        Log.e(tag, "Error processing subscription request", e)
                        false
                    }

                    Log.d(tag, "Subscription request result: $success")
                }

                InAppActionType.CLOSE -> {
                    // The message is dismissed by the caller of this function,
                    // so no specific action is needed here for CLOSE.
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to handle action", e)
            Toast.makeText(context, "Error performing action", Toast.LENGTH_SHORT).show()
        }
    }
}

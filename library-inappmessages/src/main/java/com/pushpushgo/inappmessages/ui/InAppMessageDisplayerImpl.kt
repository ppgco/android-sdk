package com.pushpushgo.inappmessages.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.core.net.toUri
import com.pushpushgo.inappmessages.R
import com.pushpushgo.inappmessages.model.ActionType
import com.pushpushgo.inappmessages.model.InAppAction
import com.pushpushgo.inappmessages.model.InAppMessage
import com.pushpushgo.inappmessages.model.InAppMessageDisplayType
import com.pushpushgo.inappmessages.model.IntentActionType
import com.pushpushgo.inappmessages.persistence.InAppMessagePersistence
import com.pushpushgo.inappmessages.ui.composables.InAppMessageContent
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
import kotlinx.coroutines.isActive
import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext

internal class InAppMessageDisplayerImpl(
    private val persistence: InAppMessagePersistence? = null,
    private val onMessageDismissed: () -> Unit,
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

        val delayMs = message.timeSettings.showAfterDelay
        if (delayMs > 0) {
            Log.d(tag, "Scheduling message ${message.id} (delay: ${delayMs}ms) for activity: ${activity.localClassName}")
            val activityRef = WeakReference(activity)

            val newJob = launch { // This is a CoroutineScope.launch
                try {
                    Log.d(tag, "Job for ${message.id} [${this.coroutineContext[Job]}]: Starting delay of $delayMs ms.")
                    delay(delayMs)
                    Log.d(tag, "Job for ${message.id} [${this.coroutineContext[Job]}]: Delay finished.")

                    val currentActivity = activityRef.get()
                    if (currentActivity == null || currentActivity.isFinishing) {
                        Log.d(tag, "Job for ${message.id} [${this.coroutineContext[Job]}]: Activity ${activity.localClassName} no longer available or finishing.")
                        return@launch
                    }

                    if (!isActive) { // Check if job was cancelled during delay
                        Log.d(tag, "Job for ${message.id} [${this.coroutineContext[Job]}]: Job was cancelled during delay (isActive=false).")
                        return@launch
                    }

                    Log.d(tag, "Job for ${message.id} [${this.coroutineContext[Job]}]: Showing message on activity ${currentActivity.localClassName}.")
                    withContext(Dispatchers.Main) { // Ensure UI ops on Main
                        if (shouldBeDisplayed(message)) {
                            showMessageByType(currentActivity, message)
                        }
                    }
                } catch (e: CancellationException) {
                    Log.d(tag, "Job for ${message.id} [${this.coroutineContext[Job]}] was cancelled: ${e.message}")
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
                    showMessageByType(activity, message)
                }
            }
        }
    }

    private suspend fun showMessageByType(activity: Activity, message: InAppMessage) {
        // Dismiss any currently visible message before showing a new one
        hideMessage()

        when (message.displayType) {
            InAppMessageDisplayType.CARD,
            InAppMessageDisplayType.FULLSCREEN -> showModal(activity, message)
            InAppMessageDisplayType.BANNER -> showBanner(activity, message)
            InAppMessageDisplayType.MODAL -> showModal(activity, message)
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
        } finally {
            // Allow the next dismissal call after this one has fully completed
            isDismissing = false
        }
    }

    private suspend fun showBanner(activity: Activity, message: InAppMessage) {
        if (shouldBeDisplayed(message).not()) return

        // Ensure UI operations are on the main thread
        withContext(Dispatchers.Main) {
            currentDialog?.dismiss()

            val composeView = createComposeView(activity, message)
            val dialog = Dialog(activity, R.style.InAppMessageDialog_Banner).apply {
                setContentView(composeView)
                setCancelable(message.dismissible)
                setOnDismissListener { if (currentDialog == this) dismissMessage(message) }
            }
            dialog.show()
            currentDialog = dialog
        }
    }

    private suspend fun showModal(activity: Activity, message: InAppMessage) {
        if (shouldBeDisplayed(message).not()) return

        // Ensure UI operations are on the main thread
        withContext(Dispatchers.Main) {
            currentDialog?.dismiss()

            val composeView = createComposeView(activity, message)
            val dialog = Dialog(activity, R.style.InAppMessageDialog_Modal).apply {
                setContentView(composeView)
                setCancelable(message.dismissible)
                setOnDismissListener { if (currentDialog == this) dismissMessage(message) }
            }
            dialog.show()
            currentDialog = dialog
        }
    }

    private suspend fun shouldBeDisplayed(message: InAppMessage): Boolean {
        val isDismissed = withContext(Dispatchers.IO) {
            persistence?.isMessageDismissed(message.id)
        }
        if (isDismissed == true && !message.timeSettings.showAgain) {
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
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
            }
            setContent {
                InAppMessageContent(
                    message = message,
                    onDismiss = { dismissMessage(message) },
                    onAction = { action ->
                        handleAction(activity, action)
                        dismissMessage(message)
                    }
                )
            }
        }
    }

    private fun handleAction(context: Context, action: InAppAction) {
        try {
            when (action.actionType) {
                ActionType.URL -> {
                    action.url?.takeIf { it.isNotEmpty() }?.let {
                        context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri()).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    } ?: Log.e(tag, "URL is null or empty in payload")
                }
                ActionType.INTENT -> {
                    createIntentForAction(context, action)?.let { intent ->
                        context.startActivity(intent)
                    } ?: run {
                        Log.e(tag, "Could not create intent. Action details: $action")
                        Toast.makeText(context, "Invalid action configuration", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to handle action", e)
            Toast.makeText(context, "Error performing action", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createIntentForAction(context: Context, action: InAppAction): Intent? {
        val intentAction = when (action.intentAction) {
            IntentActionType.VIEW, IntentActionType.GEO -> Intent.ACTION_VIEW
            IntentActionType.DIAL -> Intent.ACTION_DIAL
            IntentActionType.SENDTO -> Intent.ACTION_SENDTO
            IntentActionType.SETTINGS -> android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            null -> null
        } ?: return null // Return null if intentAction is not recognized

        val intentData = when (action.intentAction) {
            IntentActionType.SETTINGS -> "package:${context.packageName}".toUri()
            IntentActionType.DIAL -> action.uri?.let { "tel:$it".toUri() }
            IntentActionType.SENDTO -> action.uri?.let { "mailto:$it".toUri() }
            IntentActionType.GEO -> action.uri?.let { "geo:$it".toUri() }
            IntentActionType.VIEW -> action.uri?.toUri()
            null -> null
        }

        return Intent().apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            this.action = intentAction
            this.data = intentData
        }
    }
}

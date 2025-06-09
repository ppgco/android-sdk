package com.pushpushgo.inappmessages.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.pushpushgo.inappmessages.R
import com.pushpushgo.inappmessages.model.ActionType
import com.pushpushgo.inappmessages.model.InAppAction
import com.pushpushgo.inappmessages.model.InAppMessage
import com.pushpushgo.inappmessages.persistence.InAppMessagePersistence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import kotlin.coroutines.CoroutineContext

class InAppMessageDisplayerImpl(
    private val persistence: InAppMessagePersistence? = null
) : InAppMessageDisplayer, CoroutineScope {
    
    private val tag = "InAppMsgDisplayer"
    
    // Coroutine context and job for managing message display jobs
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job
    
    // Store current UI elements
    private var currentDialog: Dialog? = null
    
    // Map to store pending delayed message jobs
    private val pendingMessages = mutableMapOf<String, Job>()
    
    override fun showMessage(activity: Activity, message: InAppMessage) {
        // Cancel any existing pending job for this message
        pendingMessages[message.id]?.cancel()
        pendingMessages.remove(message.id)
        
        val delay = message.timeSettings.showAfterDelay
        if (delay > 0) {
            Log.d(tag, "Scheduling message ${message.id} to show after $delay ms")
            
            // Store activity as weak reference to prevent memory leaks
            val activityRef = WeakReference(activity)
            
            // Schedule delayed display using coroutines
            val job = launch {
                delay(delay)
                
                // Get activity from weak reference
                val activityInstance = activityRef.get()
                if (activityInstance == null || activityInstance.isFinishing) {
                    Log.d(tag, "Activity no longer available, not showing message ${message.id}")
                    return@launch
                }
                
                // Show the message after delay
                Log.d(tag, "Showing message ${message.id} after delay")
                withContext(Dispatchers.Main) {
                    showMessageByType(activityInstance, message)
                }
                
                pendingMessages.remove(message.id)
            }
            
            // Store job to allow cancellation if needed
            pendingMessages[message.id] = job
        } else {
            // Show immediately if no delay
            showMessageByType(activity, message)
        }
    }
    
    private fun showMessageByType(activity: Activity, message: InAppMessage) {
        when (message.template.lowercase()) {
            "banner" -> showBanner(activity, message)
            "modal" -> showModal(activity, message)
            "tooltip" -> showTooltip(activity, message)
            else -> showBanner(activity, message) // fallback
        }
    }
    
    override fun cancelPendingMessages() {
        Log.d(tag, "Cancelling ${pendingMessages.size} pending delayed messages")
        
        // Cancel all pending coroutine jobs
        pendingMessages.forEach { (messageId, job) -> 
            job.cancel()
            Log.d(tag, "Cancelled pending delayed message: $messageId")
        }
        
        // Clear the collection
        pendingMessages.clear()
    }

    override fun dismissMessage(message: InAppMessage) {
        currentDialog?.dismiss()
        currentDialog = null
        
        // Launch in IO context to handle persistence operations
        launch(Dispatchers.IO) {
            if (message.timeSettings.showAgain) {
                // For showAgain messages, record the timestamp for cooldown calculation
                val now = System.currentTimeMillis()
                persistence?.setLastShownAt(message.id, now)
                Log.d(tag, "dismissMessage: Set lastShownAt for ${message.id} to $now")
            } else {
                // For one-time messages, mark as dismissed permanently
                persistence?.markMessageDismissed(message.id)
                Log.d(tag, "dismissMessage: Marked message ${message.id} as dismissed")
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun showBanner(activity: Activity, message: InAppMessage) {
        // Run eligibility check in IO context first, then switch to UI
        launch {
            // Check in IO context if message should be shown
            val shouldShow = withContext(Dispatchers.IO) {
                !(message.timeSettings.showAgain.not() && persistence?.isMessageDismissed(message.id) == true)
            }
            
            if (!shouldShow) {
                Log.d(tag, "showBanner: Message ${message.id} is dismissed and showAgain is false, not showing.")
                return@launch
            }
            
            // UI operations must be on Main thread
            withContext(Dispatchers.Main) {
                val inflater = LayoutInflater.from(activity)
                val view = inflater.inflate(R.layout.inapp_message_banner, null)
                bindMessageView(view, message)
                val dialog = Dialog(activity, R.style.InAppMessageDialog_Banner)
                dialog.setContentView(view)
                dialog.setCancelable(message.dismissible)
                dialog.setOnDismissListener {
                    // Only call dismissMessage if dialog is being dismissed by user (not programmatically)
                    if (currentDialog != null) {
                        dismissMessage(message)
                    }
                }
                dialog.show()
                currentDialog = dialog
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun showModal(activity: Activity, message: InAppMessage) {
        // Run eligibility check in IO context first, then switch to UI
        launch {
            // Check in IO context if message should be shown
            val shouldShow = withContext(Dispatchers.IO) {
                !(message.timeSettings.showAgain.not() && persistence?.isMessageDismissed(message.id) == true)
            }
            
            if (!shouldShow) {
                Log.d(tag, "showModal: Message ${message.id} is dismissed and showAgain is false, not showing.")
                return@launch
            }
            
            // UI operations must be on Main thread
            withContext(Dispatchers.Main) {
                // Dismiss any existing dialog
                currentDialog?.dismiss()
                
                val inflater = LayoutInflater.from(activity)
                val view = inflater.inflate(R.layout.inapp_message_modal, null)
                bindMessageView(view, message)
                val dialog = Dialog(activity, R.style.InAppMessageDialog_Modal)
                dialog.setContentView(view)
                dialog.setCancelable(message.dismissible)
                dialog.setOnDismissListener {
                    // Only call dismissMessage if dialog is being dismissed by user (not programmatically)
                    if (currentDialog == dialog) {
                        dismissMessage(message)
                        currentDialog = null
                    }
                }
                dialog.show()
                currentDialog = dialog
            }
        }
    }

    /**
     * Show a tooltip message. If anchorView is provided, show a PopupWindow anchored to the view.
     * Otherwise, fallback to Toast.
     */
    @SuppressLint("InflateParams")
    private fun showTooltip(activity: Activity, message: InAppMessage, anchorView: View? = null) {
        launch {
            // Check in IO context if message should be shown
            val shouldShow = withContext(Dispatchers.IO) {
                !(message.timeSettings.showAgain.not() && persistence?.isMessageDismissed(message.id) == true)
            }
            
            if (!shouldShow) {
                Log.d(tag, "showTooltip: Message ${message.id} is dismissed and showAgain is false, not showing.")
                return@launch
            }
            
            withContext(Dispatchers.Main) {
                if (anchorView != null) {
                    showTooltipWithAnchor(activity, message, anchorView)
                } else {
                    Toast.makeText(
                        activity,
                        message.description,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    /**
     * Display tooltip anchored to a specific view
     */
    @SuppressLint("InflateParams")
    private fun showTooltipWithAnchor(activity: Activity, message: InAppMessage, anchorView: View) {
        val inflater = LayoutInflater.from(activity)
        val tooltipView = inflater.inflate(R.layout.inapp_message_tooltip, null)
        val textView = tooltipView.findViewById<TextView>(R.id.inapp_tooltip_text)
        textView.text = message.description
        
        val popup = PopupWindow(tooltipView, ViewGroup.LayoutParams.WRAP_CONTENT, 
                                ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.elevation = 8f
        tooltipView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        
        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)
        val yOffset = -tooltipView.measuredHeight
        popup.showAsDropDown(anchorView, 0, yOffset)
        
        // Auto-dismiss after 5 seconds
        launch {
            delay(5000)
            withContext(Dispatchers.Main) {
                if (popup.isShowing) {
                    popup.dismiss()
                }
            }
        }
    }

    /**
     * Binds message data to the provided view.
     */
    private fun bindMessageView(view: View, message: InAppMessage) {
        val titleView = view.findViewById<TextView>(R.id.inapp_message_title)
        val messageView = view.findViewById<TextView>(R.id.inapp_message_body)
        val button = view.findViewById<Button>(R.id.inapp_message_action_button)

        // Set text content
        titleView?.text = message.title
        messageView?.text = message.description

        // Configure action button if actions are available
        val action = message.actions.firstOrNull()
        if (action != null) {
            button?.apply {
                isVisible = true
                text = getButtonText(action.actionType)
                setOnClickListener {
                    handleAction(view.context, action)
                }
            }
        } else {
            button?.isVisible = false
        }
    }
    
    /**
     * Get button text based on action type
     */
    private fun getButtonText(actionType: ActionType): String = when (actionType) {
        ActionType.URL -> "Open"
        ActionType.INTENT -> "Intent"
    }
    
    /**
     * Handle action when a button is clicked
     */
    private fun handleAction(context: android.content.Context, action: InAppAction) {
        when (action.actionType) {
            ActionType.URL -> {
                val url = action.payload["url"] as? String
                if (!url.isNullOrEmpty()) {
                    launchUrl(context, url)
                } else {
                    Log.w(tag, "URL action provided with empty URL")
                }
            }
            ActionType.INTENT -> {
                val intentName = action.payload["intentName"] as? String
                if (!intentName.isNullOrEmpty()) {
                    launchIntent(context, intentName, action.payload["extras"] as? Map<*, *>)
                } else {
                    Log.w(tag, "Intent action provided with empty intent name")
                }
            }
        }
    }
    
    /**
     * Launch a URL in the browser
     */
    private fun launchUrl(context: android.content.Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = url.toUri()
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(tag, "Error launching URL: ${e.message}")
        }
    }
    
    /**
     * Launch an intent with extras
     */
    private fun launchIntent(context: android.content.Context, intentName: String, extras: Map<*, *>?) {
        try {
            val intent = Intent(intentName)
            extras?.forEach { (k, v) ->
                if (k is String && v is String) intent.putExtra(k, v)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(tag, "Error launching intent: ${e.message}")
        }
    }
}

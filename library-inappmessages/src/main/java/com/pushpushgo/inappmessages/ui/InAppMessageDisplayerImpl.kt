package com.pushpushgo.inappmessages.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import com.pushpushgo.inappmessages.R
import com.pushpushgo.inappmessages.model.InAppMessage
import com.pushpushgo.inappmessages.model.ActionType
import androidx.core.net.toUri
import com.pushpushgo.inappmessages.persistence.InAppMessagePersistence
import com.pushpushgo.inappmessages.manager.InAppMessageManager
import java.lang.ref.WeakReference

class InAppMessageDisplayerImpl(
    private val persistence: InAppMessagePersistence? = null,
    private val manager: InAppMessageManager? = null
) : InAppMessageDisplayer {
    private var currentDialog: Dialog? = null
    
    // Map to store pending delayed messages and their handlers
    private val pendingMessages = mutableMapOf<String, Handler>()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    override fun showMessage(activity: Activity, message: InAppMessage) {
        // Cancel any existing pending show operation for this message
        pendingMessages[message.id]?.removeCallbacksAndMessages(null)
        
        val delay = message.timeSettings.showAfterDelay
        if (delay > 0) {
            android.util.Log.d("InAppMsgDisplayer", "Scheduling message ${message.id} to show after $delay ms")
            
            // Store activity as weak reference to prevent memory leaks
            val activityRef = WeakReference(activity)
            
            // Schedule delayed display
            mainHandler.postDelayed({
                pendingMessages.remove(message.id)
                
                // Get activity from weak reference
                val activityInstance = activityRef.get()
                if (activityInstance == null || activityInstance.isFinishing) {
                    android.util.Log.d("InAppMsgDisplayer", "Activity no longer available, not showing message ${message.id}")
                    return@postDelayed
                }
                
                // Show the message after delay
                android.util.Log.d("InAppMsgDisplayer", "Showing message ${message.id} after delay")
                showMessageByType(activityInstance, message)
            }, delay)
            
            // Store handler to allow cancellation if needed
            pendingMessages[message.id] = mainHandler
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
        android.util.Log.d("InAppMsgDisplayer", "Cancelling ${pendingMessages.size} pending delayed messages")
        
        // Cancel all pending handlers
        pendingMessages.forEach { (messageId, handler) -> 
            handler.removeCallbacksAndMessages(null)
            android.util.Log.d("InAppMsgDisplayer", "Cancelled pending delayed message: $messageId")
        }
        
        // Clear the collection
        pendingMessages.clear()
    }

    override fun dismissMessage(message: InAppMessage) {
        currentDialog?.dismiss()
        currentDialog = null
        if (message.timeSettings.showAgain) {
            // For showAgain messages, record the timestamp for cooldown calculation
            val now = System.currentTimeMillis()
            persistence?.setLastShownAt(message.id, now)
            android.util.Log.d("InAppMsgDisplayer", "dismissMessage: Set lastShownAt for ${message.id} to $now")
        } else {
            // Regular dismissal (permanent) for one-time messages
            persistence?.markMessageDismissed(message.id)
        }
        manager?.refreshActiveMessages()
    }

    @SuppressLint("InflateParams")
    private fun showBanner(activity: Activity, message: InAppMessage) {
        // Skip display if message is dismissed and not set to show again
        if (message.timeSettings.showAgain.not() && persistence?.isMessageDismissed(message.id) == true) {
            android.util.Log.d("InAppMsgDisplayer", "showBanner: Message ${message.id} is dismissed and showAgain is false, not showing.")
            return
        }
        
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

    @SuppressLint("InflateParams")
    private fun showModal(activity: Activity, message: InAppMessage) {
        // Skip display if message is dismissed and not set to show again
        if (message.timeSettings.showAgain.not() && persistence?.isMessageDismissed(message.id) == true) {
            android.util.Log.d("InAppMsgDisplayer", "showModal: Message ${message.id} is dismissed and showAgain is false, not showing.")
            return
        }

        val inflater = LayoutInflater.from(activity)
        val view = inflater.inflate(R.layout.inapp_message_modal, null)
        bindMessageView(view, message)
        val dialog = Dialog(activity, R.style.InAppMessageDialog_Modal)
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

    /**
     * Show a tooltip message. If anchorView is provided, show a PopupWindow anchored to the view.
     * Otherwise, fallback to Toast.
     * You can extend this method to support custom anchor logic.
     */
    @SuppressLint("InflateParams")
    fun showTooltip(activity: Activity, message: InAppMessage, anchorView: View? = null) {
        // Skip display if message is dismissed and not set to show again
        if (message.timeSettings.showAgain.not() && persistence?.isMessageDismissed(message.id) == true) {
            android.util.Log.d("InAppMsgDisplayer", "showTooltip: Message ${message.id} is dismissed and showAgain is false, not showing.")
            return
        }

        if (anchorView != null) {
            val inflater = LayoutInflater.from(activity)
            val tooltipView = inflater.inflate(R.layout.inapp_message_tooltip, null)
            val textView = tooltipView.findViewById<TextView>(R.id.inapp_tooltip_text)
            textView.text = message.name
            val popup = PopupWindow(tooltipView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
            popup.elevation = 8f
            tooltipView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val location = IntArray(2)
            anchorView.getLocationOnScreen(location)
            val yOffset = -tooltipView.measuredHeight
            popup.showAsDropDown(anchorView, 0, yOffset)
        } else {
            Toast.makeText(
                activity,
                message.name,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Binds message data to the provided view.
     * Extend this method to customize how data is presented or styled.
     */
    private fun bindMessageView(view: View, message: InAppMessage) {
        val titleView = view.findViewById<TextView>(R.id.inapp_message_title)
        val messageView = view.findViewById<TextView>(R.id.inapp_message_body)
        val button = view.findViewById<Button>(R.id.inapp_message_action_button)

        titleView?.text = message.name
        messageView?.text = message.template

        val action = message.actions.firstOrNull()
        if (action != null) {
            button?.isVisible = true
            button?.text = when (action.actionType) {
                ActionType.URL -> "Open"
                ActionType.INTENT -> "Intent"
            }
            button?.setOnClickListener {
                when (action.actionType) {
                    ActionType.URL -> {
                        val url = action.payload["url"] as? String
                        if (!url.isNullOrEmpty()) {
                            val context = view.context
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                            intent.data = url.toUri()
                            context.startActivity(intent)
                        }
                    }
                    ActionType.INTENT -> {
                        val intentName = action.payload["intentName"] as? String
                        val context = view.context
                        if (!intentName.isNullOrEmpty()) {
                            try {
                                val intent = android.content.Intent(intentName)
                                (action.payload["extras"] as? Map<*, *>)?.forEach { (k, v) ->
                                    if (k is String && v is String) intent.putExtra(k, v)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Optionally log or handle
                            }
                        }
                    }
                }
            }
        } else {
            button?.isVisible = false
        }
    }
}

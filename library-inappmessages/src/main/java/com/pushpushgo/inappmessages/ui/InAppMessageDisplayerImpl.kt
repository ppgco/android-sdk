package com.pushpushgo.inappmessages.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
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

/**
 * Default implementation for displaying in-app messages as banners, modals, or tooltips.
 * Extend or replace this class to customize UI/UX for your app.
 */
class InAppMessageDisplayerImpl : InAppMessageDisplayer {
    private var currentDialog: Dialog? = null

    override fun showMessage(activity: Activity, message: InAppMessage) {
        when (message.template.lowercase()) {
            "banner" -> showBanner(activity, message)
            "modal" -> showModal(activity, message)
            "tooltip" -> showTooltip(activity, message)
            else -> showBanner(activity, message) // fallback
        }
    }

    override fun dismissMessage(messageId: String) {
        currentDialog?.dismiss()
        currentDialog = null
    }

    @SuppressLint("InflateParams")
    private fun showBanner(activity: Activity, message: InAppMessage) {
        val inflater = LayoutInflater.from(activity)
        val view = inflater.inflate(R.layout.inapp_message_banner, null)
        bindMessageView(view, message)
        val dialog = Dialog(activity, R.style.InAppMessageDialog_Banner)
        dialog.setContentView(view)
        dialog.setCancelable(message.dismissible)
        dialog.show()
        currentDialog = dialog
    }

    @SuppressLint("InflateParams")
    private fun showModal(activity: Activity, message: InAppMessage) {
        val inflater = LayoutInflater.from(activity)
        val view = inflater.inflate(R.layout.inapp_message_modal, null)
        bindMessageView(view, message)
        val dialog = Dialog(activity, R.style.InAppMessageDialog_Modal)
        dialog.setContentView(view)
        dialog.setCancelable(message.dismissible)
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
        if (anchorView != null) {
            val inflater = LayoutInflater.from(activity)
            val tooltipView = inflater.inflate(R.layout.inapp_message_tooltip, null)
            val textView = tooltipView.findViewById<TextView>(R.id.inapp_tooltip_text)
            textView.text = message.name
            // Styling
            message.style?.let { style ->
                val bgColor = style.backgroundColor ?: Color.DKGRAY
                val textColor = style.textColor ?: Color.WHITE
                tooltipView.setBackgroundColor(bgColor)
                textView.setTextColor(textColor)
            }
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
        // Optionally add styling logic here if needed (from payload or template)
    }
}

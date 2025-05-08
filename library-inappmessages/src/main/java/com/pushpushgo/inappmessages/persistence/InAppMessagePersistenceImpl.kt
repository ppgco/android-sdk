package com.pushpushgo.inappmessages.persistence

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class InAppMessagePersistenceImpl(context: Context) : InAppMessagePersistence {
    private val prefs: SharedPreferences = context.getSharedPreferences("in_app_messages_prefs", Context.MODE_PRIVATE)

    override fun isMessageDismissed(messageId: String): Boolean =
        prefs.getBoolean("dismissed_$messageId", false)

    override fun markMessageDismissed(messageId: String) {
        prefs.edit { putBoolean("dismissed_$messageId", true) }
    }

    override fun isMessageExpired(messageId: String): Boolean =
        prefs.getBoolean("expired_$messageId", false)

    override fun markMessageExpired(messageId: String) {
        prefs.edit { putBoolean("expired_$messageId", true) }
    }
}

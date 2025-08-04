package com.pushpushgo.inappmessages.persistence

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class InAppMessagePersistenceImpl(
  context: Context,
) : InAppMessagePersistence {
  private val prefs: SharedPreferences = context.getSharedPreferences("in_app_messages_prefs", Context.MODE_PRIVATE)

  override fun isMessageDismissed(messageId: String): Boolean = prefs.getBoolean("dismissed_$messageId", false)

  override fun markMessageDismissed(messageId: String) {
    prefs.edit { putBoolean("dismissed_$messageId", true) }
    setLastDismissedAt(messageId, System.currentTimeMillis())
  }

  override fun isMessageExpired(messageId: String): Boolean = prefs.getBoolean("expired_$messageId", false)

  override fun markMessageExpired(messageId: String) {
    prefs.edit { putBoolean("expired_$messageId", true) }
  }

  override fun getLastDismissedAt(messageId: String): Long? =
    if (prefs.contains("last_dismissed_$messageId")) prefs.getLong("last_dismissed_$messageId", 0L) else null

  override fun setLastDismissedAt(
    messageId: String,
    timestamp: Long,
  ) {
    prefs.edit {
      putLong("last_dismissed_$messageId", timestamp)
    }
  }

  override fun getFirstEligibleAt(messageId: String): Long? =
    if (prefs.contains("first_eligible_at_$messageId")) prefs.getLong("first_eligible_at_$messageId", 0L) else null

  override fun setFirstEligibleAt(
    messageId: String,
    timestamp: Long,
  ) {
    prefs.edit { putLong("first_eligible_at_$messageId", timestamp) }
  }

  override fun resetFirstEligibleAt(messageId: String) {
    prefs.edit { remove("first_eligible_at_$messageId") }
  }
}

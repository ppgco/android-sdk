package com.pushpushgo.inappmessages.persistence

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.pushpushgo.inappmessages.model.InAppMessage
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.pushpushgo.inappmessages.utils.ZonedDateTimeAdapter

class InAppMessagePersistenceImpl(
  context: Context,
  private val moshi: Moshi = Moshi.Builder()
    .add(ZonedDateTimeAdapter.FACTORY)
    .addLast(KotlinJsonAdapterFactory())
    .build()
) : InAppMessagePersistence {
  private val prefs: SharedPreferences = context.getSharedPreferences("in_app_messages_prefs", Context.MODE_PRIVATE)
  
  // JSON adapter for messages list serialization
  private val listType = Types.newParameterizedType(List::class.java, InAppMessage::class.java)
  private val messagesAdapter: JsonAdapter<List<InAppMessage>> = moshi.adapter(listType)
  
  companion object {
    private const val KEY_ETAG = "etag"
    private const val KEY_CACHED_MESSAGES = "cached_messages"
    private const val KEY_CACHE_TIMESTAMP = "cache_timestamp"
    
    // Cache expiry - after 24h force refresh even with same ETag
    private const val CACHE_EXPIRY_MS = 24 * 60 * 60 * 1000L
  }

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
  
  // ETag caching implementation for HTTP cache optimization
  override fun getStoredETag(): String? {
    val timestamp = prefs.getLong(KEY_CACHE_TIMESTAMP, 0)
    val isExpired = System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS
    
    return if (isExpired) {
      // Cache expired - clear and return null to force fresh fetch
      clearCache()
      null
    } else {
      prefs.getString(KEY_ETAG, null)
    }
  }
  
  override fun saveCache(etag: String, messages: List<InAppMessage>) {
    val messagesJson = messagesAdapter.toJson(messages)
    
    prefs.edit {
      putString(KEY_ETAG, etag)
      putString(KEY_CACHED_MESSAGES, messagesJson)
      putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
    }
  }
  
  override fun getCachedMessages(): List<InAppMessage>? {
    val messagesJson = prefs.getString(KEY_CACHED_MESSAGES, null) ?: return null
    
    return try {
      messagesAdapter.fromJson(messagesJson) ?: emptyList()
    } catch (e: Exception) {
      // JSON parsing failed - clear cache and return null
      clearCache()
      null
    }
  }
  
  override fun clearCache() {
    prefs.edit {
      remove(KEY_ETAG)
      remove(KEY_CACHED_MESSAGES)
      remove(KEY_CACHE_TIMESTAMP)
    }
  }
}

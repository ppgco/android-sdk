package com.pushpushgo.inappmessages.persistence

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.pushpushgo.inappmessages.model.InAppMessage
import com.pushpushgo.inappmessages.utils.ZonedDateTimeAdapter
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

internal class InAppMessagePersistenceImpl(
  context: Context,
  private val debug: Boolean = false,
  private val moshi: Moshi =
    Moshi
      .Builder()
      .add(ZonedDateTimeAdapter.FACTORY)
      .addLast(KotlinJsonAdapterFactory())
      .build(),
) : InAppMessagePersistence {
  private val prefs: SharedPreferences = context.getSharedPreferences("in_app_messages_prefs", Context.MODE_PRIVATE)

  // JSON adapter for messages list serialization
  private val listType = Types.newParameterizedType(List::class.java, InAppMessage::class.java)
  private val messagesAdapter: JsonAdapter<List<InAppMessage>> = moshi.adapter(listType)

  companion object {
    private const val TAG = "InAppMsgPersistence"
    private const val KEY_ETAG = "etag"
    private const val KEY_CACHED_MESSAGES = "cached_messages"
    private const val KEY_CACHE_TIMESTAMP = "cache_timestamp"

    // Cache expiry - after 24h force refresh even with same ETag
    private const val CACHE_EXPIRY_MS = 24 * 60 * 60 * 1000L
  }

  override fun isMessageDismissed(messageId: String): Boolean = prefs.getBoolean("dismissed_$messageId", false)

  override fun markMessageDismissed(messageId: String) {
    if (debug) {
      Log.d(TAG, "Marking message [$messageId] as dismissed")
    }
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
      if (debug) {
        Log.d(TAG, "Cache expired, clearing and forcing fresh fetch")
      }
      clearCache()
      null
    } else {
      val etag = prefs.getString(KEY_ETAG, null)
      if (debug) {
        Log.d(TAG, "Retrieved stored ETag: ${etag ?: "none"}")
      }
      etag
    }
  }

  override fun saveCache(
    etag: String,
    messages: List<InAppMessage>,
  ) {
    val messagesJson = messagesAdapter.toJson(messages)
    if (debug) {
      Log.d(TAG, "Saving cache: ETag=$etag, ${messages.size} messages")
    }

    prefs.edit {
      putString(KEY_ETAG, etag)
      putString(KEY_CACHED_MESSAGES, messagesJson)
      putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
    }
  }

  override fun getCachedMessages(): List<InAppMessage>? {
    val messagesJson = prefs.getString(KEY_CACHED_MESSAGES, null) ?: return null

    return try {
      val messages = messagesAdapter.fromJson(messagesJson) ?: emptyList()
      if (debug) {
        Log.d(TAG, "Retrieved ${messages.size} cached messages")
      }
      messages
    } catch (_: Exception) {
      // JSON parsing failed - clear cache and return null
      if (debug) {
        Log.d(TAG, "Failed to parse cached messages, clearing cache")
      }
      clearCache()
      null
    }
  }

  override fun clearCache() {
    if (debug) {
      Log.d(TAG, "Clearing cache")
    }
    prefs.edit {
      remove(KEY_ETAG)
      remove(KEY_CACHED_MESSAGES)
      remove(KEY_CACHE_TIMESTAMP)
    }
  }
}

package com.pushpushgo.sdk.inapp.persistence

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.pushpushgo.sdk.inapp.InAppMessages
import com.pushpushgo.sdk.inapp.model.InAppMessage
import com.pushpushgo.sdk.inapp.utils.ZonedDateTimeAdapter
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * Message state keyed by message id (dismissals, eligibility) is shared by all projects, since
 * message ids are unique. The message cache belongs to [projectId], so switching projects never
 * serves messages of another project - not even as an offline fallback.
 */
internal class InAppMessagePersistenceImpl(
  context: Context,
  projectId: String,
  private val debug: Boolean = false,
  private val moshi: Moshi =
    Moshi
      .Builder()
      .add(ZonedDateTimeAdapter.FACTORY)
      .build(),
) : InAppMessagePersistence {
  private val prefs: SharedPreferences = context.getSharedPreferences("in_app_messages_prefs", Context.MODE_PRIVATE)

  // JSON adapter for messages list serialization
  private val listType = Types.newParameterizedType(List::class.java, InAppMessage::class.java)
  private val messagesAdapter: JsonAdapter<List<InAppMessage>> = moshi.adapter(listType)

  private val keyEtag = "etag_$projectId"
  private val keyCachedMessages = "cached_messages_$projectId"
  private val keyCacheTimestamp = "cache_timestamp_$projectId"

  companion object {
    // Cache keys of SDK versions that did not scope the cache by project
    private const val LEGACY_KEY_ETAG = "etag"
    private const val LEGACY_KEY_CACHED_MESSAGES = "cached_messages"
    private const val LEGACY_KEY_CACHE_TIMESTAMP = "cache_timestamp"

    // Cache expiry - after 24h force refresh even with same ETag
    private const val CACHE_EXPIRY_MS = 24 * 60 * 60 * 1000L
  }

  init {
    // The legacy cache does not say which project it belongs to
    if (prefs.contains(LEGACY_KEY_CACHED_MESSAGES) || prefs.contains(LEGACY_KEY_ETAG)) {
      prefs.edit {
        remove(LEGACY_KEY_ETAG)
        remove(LEGACY_KEY_CACHED_MESSAGES)
        remove(LEGACY_KEY_CACHE_TIMESTAMP)
      }
    }
  }

  override fun isMessageDismissed(messageId: String): Boolean = prefs.getBoolean("dismissed_$messageId", false)

  override fun markMessageDismissed(messageId: String) {
    if (debug) {
      Log.d(InAppMessages.TAG, "[Persistence] Marking message [$messageId] as dismissed")
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
    val timestamp = prefs.getLong(keyCacheTimestamp, 0)
    val isExpired = System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS

    return if (isExpired) {
      // Cache expired - clear and return null to force fresh fetch
      if (debug) {
        Log.d(InAppMessages.TAG, "[Persistence] Cache expired, clearing and forcing fresh fetch")
      }
      clearCache()
      null
    } else {
      val etag = prefs.getString(keyEtag, null)
      if (debug) {
        Log.d(InAppMessages.TAG, "[Persistence] Retrieved stored ETag: ${etag ?: "none"}")
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
      Log.d(InAppMessages.TAG, "[Persistence] Saving cache: ETag=$etag, ${messages.size} messages")
    }

    prefs.edit {
      putString(keyEtag, etag)
      putString(keyCachedMessages, messagesJson)
      putLong(keyCacheTimestamp, System.currentTimeMillis())
    }
  }

  override fun getCachedMessages(): List<InAppMessage>? {
    val messagesJson = prefs.getString(keyCachedMessages, null) ?: return null

    return try {
      val messages = messagesAdapter.fromJson(messagesJson) ?: emptyList()
      if (debug) {
        Log.d(InAppMessages.TAG, "[Persistence] Retrieved ${messages.size} cached messages")
      }
      messages
    } catch (_: Exception) {
      // JSON parsing failed - clear cache and return null
      if (debug) {
        Log.d(InAppMessages.TAG, "[Persistence] Failed to parse cached messages, clearing cache")
      }
      clearCache()
      null
    }
  }

  override fun clearCache() {
    if (debug) {
      Log.d(InAppMessages.TAG, "[Persistence] Clearing cache")
    }
    prefs.edit {
      remove(keyEtag)
      remove(keyCachedMessages)
      remove(keyCacheTimestamp)
    }
  }
}

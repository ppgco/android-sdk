package com.pushpushgo.inappmessages.persistence

import com.pushpushgo.inappmessages.model.InAppMessage

interface InAppMessagePersistence {
  fun isMessageDismissed(messageId: String): Boolean

  fun markMessageDismissed(messageId: String)

  fun isMessageExpired(messageId: String): Boolean

  fun markMessageExpired(messageId: String)

  fun getLastDismissedAt(messageId: String): Long?

  fun setLastDismissedAt(
    messageId: String,
    timestamp: Long,
  )

  fun getFirstEligibleAt(messageId: String): Long?

  fun setFirstEligibleAt(
    messageId: String,
    timestamp: Long,
  )

  fun resetFirstEligibleAt(messageId: String)

  // ETag caching for HTTP cache optimization
  fun getStoredETag(): String?

  fun saveCache(
    etag: String,
    messages: List<InAppMessage>,
  )

  fun getCachedMessages(): List<InAppMessage>?

  fun clearCache()
}

package com.pushpushgo.inappmessages.repository

import android.util.Log
import com.pushpushgo.inappmessages.model.InAppMessage
import com.pushpushgo.inappmessages.network.InAppListGetApi
import com.pushpushgo.inappmessages.persistence.InAppMessagePersistence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class InAppMessageRepositoryImpl(
  private val inAppApi: InAppListGetApi,
  private val projectId: String,
  private val apiKey: String,
  private val persistence: InAppMessagePersistence,
  private val debug: Boolean = false,
) : InAppMessageRepository {
  
  companion object {
    private const val TAG = "InAppMsgRepo"
  }
  
  override suspend fun fetchMessages(): List<InAppMessage> =
    try {
      withContext(Dispatchers.IO) {
        val storedETag = persistence.getStoredETag()
        
        val response = inAppApi.getInAppMessages(
          projectId = projectId,
          apiKey = apiKey,
          ifNoneMatch = storedETag
        )
        
        when (response.code()) {
          200 -> {
            // Fresh data received
            val messages = response.body()?.data ?: emptyList()
            if (debug) {
              Log.d(TAG, "Received fresh data: ${messages.size} messages")
            }
            
            // Save ETag and cache the payload
            val newETag = response.headers()["ETag"]
            if (newETag != null) {
              if (debug) {
                Log.d(TAG, "Saving cache with ETag: $newETag")
              }
              persistence.saveCache(newETag, messages)
            } else {
              Log.w(TAG, "No ETag header in response")
            }
            
            messages
          }
          
          304 -> {
            // Data not modified - use cached messages
            if (debug) {
              Log.d(TAG, "Received 304 Not Modified, using cached messages")
            }
            val cachedMessages = persistence.getCachedMessages()
            
            if (cachedMessages != null) {
              cachedMessages
            } else {
              Log.w(TAG, "304 response but no cached messages found - clearing cache")
              persistence.clearCache()
              emptyList()
            }
          }
          
          else -> {
            Log.e(TAG, "Error fetching messages from API: ${response.code()}")
            
            // On error, try to return cached messages if available
            val cachedMessages = persistence.getCachedMessages()
            if (debug && cachedMessages != null) {
              Log.d(TAG, "API error, falling back to ${cachedMessages.size} cached messages")
            }
            cachedMessages ?: emptyList()
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Exception fetching messages from API", e)
      
      // On network error, try to return cached messages if available
      val cachedMessages = persistence.getCachedMessages()
      cachedMessages ?: emptyList()
    }
}

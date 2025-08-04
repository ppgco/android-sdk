package com.pushpushgo.inappmessages.repository

import android.util.Log
import com.pushpushgo.inappmessages.model.InAppMessage
import com.pushpushgo.inappmessages.network.InAppListGetApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class InAppMessageRepositoryImpl(
  private val inAppApi: InAppListGetApi,
  private val projectId: String,
  private val apiKey: String,
) : InAppMessageRepository {
  override suspend fun fetchMessages(): List<InAppMessage> =
    try {
      withContext(Dispatchers.IO) {
        val response = inAppApi.getInAppMessages(projectId, apiKey)
        if (response.isSuccessful) {
          response.body()?.data ?: emptyList()
        } else {
          Log.e("InAppMsgRepo", "Error fetching messages from API: ${response.code()}")
          emptyList()
        }
      }
    } catch (e: Exception) {
      Log.e("InAppMsgRepo", "Error fetching messages from API", e)
      emptyList()
    }
}

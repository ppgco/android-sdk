package com.pushpushgo.sdk.inapp.event

import android.util.Log
import com.pushpushgo.sdk.inapp.InAppMessages
import com.pushpushgo.sdk.inapp.network.InAppEventApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

internal class InAppMessageEventRepository(
  private val api: InAppEventApi,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val debug: Boolean = false,
) {
  suspend fun sendEvent(
    token: String,
    projectId: String,
    event: InAppMessageEvent,
  ) = withContext(ioDispatcher) {
    val response = api.sendInAppEvent(token, projectId, event)
    if (!response.isSuccessful) {
      if (debug) {
        Log.e(InAppMessages.TAG, "[EventRepository] Failed to send event: ${response.code()} ${response.message()}")
      }

      throw HttpException(response)
    }
  }
}

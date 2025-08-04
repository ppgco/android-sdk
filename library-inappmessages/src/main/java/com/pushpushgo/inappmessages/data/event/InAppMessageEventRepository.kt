package com.pushpushgo.inappmessages.data.event

import android.util.Log
import com.pushpushgo.inappmessages.network.InAppEventApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class InAppMessageEventRepository(
  private val api: InAppEventApi,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val debug: Boolean = false,
) {
  suspend fun sendEvent(
    token: String,
    projectId: String,
    event: InAppMessageEvent,
  ) = withContext(ioDispatcher) {
    if (debug) {
      Log.d("InAppEventRepo", "Sending in-app event: $event for project: $projectId")
    }
    val response = api.sendInAppEvent(token, projectId, event)
    if (!response.isSuccessful) {
      if (debug) {
        Log.e("InAppEventRepo", "Failed to send event: ${response.code()} ${response.message()}")
      }
      throw HttpException(response)
    } else if (debug) {
      Log.d("InAppEventRepo", "Event sent successfully: $event")
    }
  }
}

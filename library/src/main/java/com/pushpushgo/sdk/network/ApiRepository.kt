package com.pushpushgo.sdk.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pushpushgo.sdk.data.Event
import com.pushpushgo.sdk.data.EventType
import com.pushpushgo.sdk.data.Payload
import com.pushpushgo.sdk.exception.PushPushException
import com.pushpushgo.sdk.network.data.TokenRequest
import com.pushpushgo.sdk.utils.getPlatformPushToken
import com.pushpushgo.sdk.utils.logDebug
import com.pushpushgo.sdk.utils.logError
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

internal class ApiRepository(
  private val apiService: ApiService,
  private val context: Context,
  private val sharedPref: SharedPreferencesHelper,
  private val projectId: String,
  private val apiKey: String,
) {
  suspend fun registerToken(
    token: String?,
    apiKey: String = this.apiKey,
    projectId: String = this.projectId,
  ) {
    logDebug("registerToken invoked: $token")
    val tokenToRegister =
      token
        ?: sharedPref.lastToken.takeIf { it.isNotEmpty() }
        ?: getPlatformPushToken(context)

    logDebug("Token to register: $tokenToRegister")

    val data =
      apiService.registerSubscriber(
        token = apiKey,
        projectId = projectId,
        body = TokenRequest(tokenToRegister),
      )
    if (data.id.isNotBlank()) {
      sharedPref.subscriberId = data.id
    }
    logDebug("RegisterSubscriber received: $data")
  }

  suspend fun unregisterSubscriber() {
    logDebug("unregisterSubscriber() invoked")

    apiService.unregisterSubscriber(
      token = apiKey,
      projectId = projectId,
      subscriberId = sharedPref.subscriberId,
    )
    sharedPref.subscriberId = ""
  }

  suspend fun unregisterSubscriber(
    projectId: String,
    token: String,
    subscriberId: String,
  ) {
    try {
      apiService.unregisterSubscriber(
        token = token,
        projectId = projectId,
        subscriberId = subscriberId,
      )
    } catch (e: PushPushException) {
      when (e.message.orEmpty()) {
        "Cannot perform operation on inactive subscriber",
        "Subscriber not belongs to given project",
        "Not Found",
        "Subscriber not found",
        -> logError(e)

        else -> throw e
      }
    }
  }

  suspend fun migrateSubscriber(
    newProjectId: String,
    newToken: String,
  ) {
    logDebug("migrateSubscriber($newProjectId, $newToken) invoked")

    if (newProjectId.isBlank() || newToken.isBlank()) {
      return logDebug("Empty new project info!")
    }

    // unregister current
    unregisterSubscriber(
      token = apiKey,
      projectId = projectId,
      subscriberId = sharedPref.subscriberId,
    )

    // register new
    registerToken(
      token = null,
      apiKey = newToken,
      projectId = newProjectId,
    )
  }

  suspend fun sendBeacon(beacon: String) {
    apiService.sendBeacon(
      token = apiKey,
      projectId = projectId,
      subscriberId = sharedPref.subscriberId,
      beacon = beacon.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()),
    )
  }

  suspend fun sendEvent(
    type: EventType,
    buttonId: Int,
    campaign: String,
    project: String?,
    subscriber: String?,
  ) {
    apiService.sendEvent(
      token = apiKey,
      projectId = project ?: projectId,
      event =
        Event(
          type = type.value,
          payload =
            Payload(
              button = buttonId,
              campaign = campaign,
              subscriber = subscriber ?: sharedPref.subscriberId,
            ),
        ),
    )
  }

  suspend fun getBitmapFromUrl(url: String?): Bitmap? {
    if (url.isNullOrBlank()) return null

    return BitmapFactory.decodeStream(
      apiService.getRawResponse(url).byteStream(),
    )
  }
}

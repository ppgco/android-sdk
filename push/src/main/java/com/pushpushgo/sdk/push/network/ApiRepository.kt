package com.pushpushgo.sdk.push.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pushpushgo.sdk.core.api.Config
import com.pushpushgo.sdk.push.data.Event
import com.pushpushgo.sdk.push.data.EventType
import com.pushpushgo.sdk.push.data.Payload
import com.pushpushgo.sdk.push.exception.PushPushException
import com.pushpushgo.sdk.push.network.data.TokenRequest
import com.pushpushgo.sdk.push.utils.getPlatformPushToken
import com.pushpushgo.sdk.push.utils.logDebug
import com.pushpushgo.sdk.push.utils.logError
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

internal class ApiRepository(
  private val context: Context,
  private val apiService: ApiService,
  private val sharedPref: SharedPreferencesHelper,
  private val config: Config,
) {
  suspend fun registerToken(
    token: String?,
    apiKey: String = config.apiKey,
    projectId: String = config.projectId,
  ) {
    logDebug("registerToken invoked: $token")
    val tokenToRegister = token ?: sharedPref.lastToken ?: getPlatformPushToken(context)

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

    val subscriberId = sharedPref.subscriberId

    if (subscriberId == null) {
      logError("Cannot unregister - empty subscriberId")
      return
    }

    apiService.unregisterSubscriber(
      token = config.apiKey,
      projectId = config.projectId,
      subscriberId = subscriberId,
    )
    sharedPref.subscriberId = ""
  }

  private suspend fun unregisterSubscriber(
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
    newApiKey: String,
  ) {
    logDebug("migrateSubscriber($newProjectId, $newApiKey) invoked")

    if (newProjectId.isBlank() || newApiKey.isBlank()) {
      return logDebug("Empty new project info!")
    }

    val subscriberId = sharedPref.subscriberId

    if (subscriberId == null) {
      logError("Cannot migrate - empty subscriberId")
      return
    }

    unregisterSubscriber(
      token = config.apiKey,
      projectId = config.projectId,
      subscriberId = subscriberId,
    )

    registerToken(
      token = null,
      apiKey = newApiKey,
      projectId = newProjectId,
    )
  }

  suspend fun sendBeacon(beacon: String) {
    val subscriberId = sharedPref.subscriberId

    if (subscriberId == null) {
      logError("Cannot send beacon - empty subscriberId")
      return
    }
    apiService.sendBeacon(
      token = config.apiKey,
      projectId = config.projectId,
      subscriberId = subscriberId,
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
    val subscriberId = subscriber ?: sharedPref.subscriberId

    if (subscriberId == null) {
      logError("Cannot send event - empty subscriberId")
      return
    }

    apiService.sendEvent(
      token = config.apiKey,
      projectId = project ?: config.projectId,
      event =
        Event(
          type = type.value,
          payload =
            Payload(
              button = buttonId,
              campaign = campaign,
              subscriber = subscriberId,
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

package com.pushpushgo.sdk.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.data.Event
import com.pushpushgo.sdk.data.EventType
import com.pushpushgo.sdk.data.Payload
import com.pushpushgo.sdk.exception.PushPushException
import com.pushpushgo.sdk.network.data.InstallationMetadata
import com.pushpushgo.sdk.network.data.LiveActivityEndpoint
import com.pushpushgo.sdk.network.data.LiveActivityEventDto
import com.pushpushgo.sdk.network.data.LiveActivityEventsRequest
import com.pushpushgo.sdk.network.data.LiveActivitySubscribeRequest
import com.pushpushgo.sdk.network.data.TokenRequest
import com.pushpushgo.sdk.utils.PlatformType
import com.pushpushgo.sdk.utils.getPlatformPushToken
import com.pushpushgo.sdk.utils.getPlatformType
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
  private val baseUrl: String,
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

  /**
   * Registers this device as a subscriber of the given live notification and
   * returns the backend-assigned LA subscriber id. The device must already be a
   * registered push subscriber (a non-empty subscriberId / push token).
   */
  suspend fun subscribeToLiveActivity(liveNotificationId: String): String {
    val response =
      apiService.subscribeLiveActivity(
        url = liveActivitySubscribersUrl(liveNotificationId),
        token = apiKey,
        body = buildLiveActivitySubscribeRequest(),
      )
    logDebug("subscribeToLiveActivity($liveNotificationId) -> ${response.id}")
    return response.id
  }

  /** Pushes a refreshed push token / endpoint for an existing LA subscriber. */
  suspend fun updateLiveActivityEndpoint(
    liveNotificationId: String,
    liveActivitySubscriberId: String,
  ) {
    apiService.updateLiveActivitySubscriberEndpoint(
      url = "${liveActivitySubscribersUrl(liveNotificationId)}/$liveActivitySubscriberId/endpoint",
      token = apiKey,
      body = buildLiveActivitySubscribeRequest(),
    )
  }

  /** Unregisters this device from the given live notification. */
  suspend fun unsubscribeFromLiveActivity(
    liveNotificationId: String,
    liveActivitySubscriberId: String,
  ) {
    apiService.unsubscribeLiveActivity(
      url = "${liveActivitySubscribersUrl(liveNotificationId)}/$liveActivitySubscriberId",
      token = apiKey,
    )
  }

  /**
   * Reports a Live Activity statistics event (e.g. `started`, `clicked`,
   * `closed`) to the dedicated live-notification events endpoint.
   */
  suspend fun sendLiveActivityEvent(
    liveNotificationId: String,
    eventType: String,
    liveDataVersion: Int,
    subscriberId: String,
  ) {
    val platform = getPlatformType().apiName
    apiService.collectLiveActivityEvents(
      url = "$baseUrl/statistics/v1/$platform/projects/$projectId/live-notifications/$liveNotificationId/events",
      token = apiKey,
      body =
        LiveActivityEventsRequest(
          installationId = sharedPref.installationId,
          subscriberId = subscriberId,
          events =
            listOf(
              LiveActivityEventDto(
                type = eventType,
                occurredAt = isoTimestamp(),
                liveDataVersion = liveDataVersion,
              ),
            ),
        ),
    )
  }

  private fun isoTimestamp(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
      .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
      .format(java.util.Date())

  /**
   * Fetches the current live notification document (configuration + live data +
   * lifecycle) as raw JSON, used to catch up a subscriber that joined after the
   * `start` push was already delivered. Returns null on any failure.
   */
  suspend fun fetchLiveActivity(liveNotificationId: String): String? =
    runCatching {
      apiService.getLiveActivity(
        url = "$baseUrl/core/projects/$projectId/live-notifications/$liveNotificationId",
        token = apiKey,
      ).string()
    }.onFailure { logError("fetchLiveActivity($liveNotificationId) failed", it) }.getOrNull()

  private fun liveActivitySubscribersUrl(liveNotificationId: String): String =
    "$baseUrl/core/projects/$projectId/live-notifications/$liveNotificationId/subscribers"

  private fun buildLiveActivitySubscribeRequest(): LiveActivitySubscribeRequest {
    val transport =
      when (getPlatformType()) {
        PlatformType.FCM -> "FCM"
        PlatformType.HCM -> "HMS"
      }
    return LiveActivitySubscribeRequest(
      installationId = sharedPref.installationId,
      installationMetadata =
        InstallationMetadata(
          sdkVersion = PushPushGo.VERSION,
          osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        ),
      endpoint =
        LiveActivityEndpoint(
          transport = transport,
          pushToken = sharedPref.lastToken,
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

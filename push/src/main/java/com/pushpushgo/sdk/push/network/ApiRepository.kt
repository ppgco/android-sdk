package com.pushpushgo.sdk.push.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pushpushgo.sdk.core.api.Config
import com.pushpushgo.sdk.push.PushNotifications
import com.pushpushgo.sdk.push.exception.PushPushException
import com.pushpushgo.sdk.push.network.data.InstallationMetadata
import com.pushpushgo.sdk.push.network.data.LiveActivityEndpoint
import com.pushpushgo.sdk.push.network.data.LiveActivityEventDto
import com.pushpushgo.sdk.push.network.data.LiveActivityEventsRequest
import com.pushpushgo.sdk.push.network.data.LiveActivitySubscribeRequest
import com.pushpushgo.sdk.push.network.data.TokenRequest
import com.pushpushgo.sdk.push.utils.PlatformType
import com.pushpushgo.sdk.push.utils.getPlatformPushToken
import com.pushpushgo.sdk.push.utils.getPlatformType
import com.pushpushgo.sdk.push.utils.logDebug
import com.pushpushgo.sdk.push.utils.logError
import com.pushpushgo.sdk.push.utils.osVersion
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

internal class ApiRepository(
  private val context: Context,
  private val apiService: ApiService,
  private val sharedPref: SharedPreferencesHelper,
  private val config: Config,
) {
  companion object {
    private const val INACTIVE_SUBSCRIBER_MESSAGE = "Cannot perform operation on inactive subscriber"
    private const val LIVE_ACTIVITY_NOT_FOUND_MESSAGE = "Live notification not found"
    private const val LIVE_ACTIVITY_SUBSCRIBER_NOT_FOUND_MESSAGE = "Live notification subscriber not found"
  }

  suspend fun registerToken(token: String?) {
    logDebug("registerToken invoked: $token")

    val tokenToRegister = token ?: getPlatformPushToken(context)

    require(tokenToRegister.isNotBlank()) { "Cannot register subscriber with an empty push token" }

    logDebug("Token to register: $tokenToRegister")

    val data =
      apiService.registerSubscriber(
        token = config.apiKey,
        projectId = config.projectId,
        body =
          TokenRequest(
            token = tokenToRegister,
            sdkVersion = PushNotifications.VERSION,
            osVersion = osVersion(),
            installationId = sharedPref.installationId,
          ),
      )

    if (data.id.isNotBlank()) {
      sharedPref.subscriberId = data.id
    }

    sharedPref.lastToken = tokenToRegister

    logDebug("RegisterSubscriber received: $data")
  }

  suspend fun unregisterSubscriber() {
    logDebug("unregisterSubscriber() invoked")

    val subscriberId = sharedPref.subscriberId

    if (subscriberId == null) {
      logError("Cannot unregister - empty subscriberId")
      return
    }

    try {
      apiService.unregisterSubscriber(
        token = config.apiKey,
        projectId = config.projectId,
        subscriberId = subscriberId,
      )
    } catch (exception: PushPushException) {
      val isAlreadyUnregistered =
        exception.statusCode == 404 ||
          (exception.statusCode == 400 && exception.message == INACTIVE_SUBSCRIBER_MESSAGE)

      if (!isAlreadyUnregistered) throw exception

      logDebug("Subscriber is already unregistered")
    }

    sharedPref.subscriberId = ""
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

  /**
   * Registers this device as a subscriber of the given live notification and
   * returns the backend-assigned LA subscriber id. The device must already be a
   * registered push subscriber (a non-empty subscriberId / push token).
   */
  suspend fun subscribeToLiveActivity(liveNotificationId: String): String {
    val response =
      apiService.subscribeLiveActivity(
        url = liveActivitySubscribersUrl(liveNotificationId),
        token = config.apiKey,
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
      token = config.apiKey,
      body = buildLiveActivitySubscribeRequest(),
    )
  }

  /** Unregisters this device from the given live notification. */
  suspend fun unsubscribeFromLiveActivity(
    liveNotificationId: String,
    liveActivitySubscriberId: String,
  ) {
    try {
      apiService.unsubscribeLiveActivity(
        url = "${liveActivitySubscribersUrl(liveNotificationId)}/$liveActivitySubscriberId",
        token = config.apiKey,
      )
    } catch (exception: PushPushException) {
      val isAlreadyUnsubscribed =
        exception.statusCode == 400 &&
          (
            exception.message == LIVE_ACTIVITY_NOT_FOUND_MESSAGE ||
              exception.message == LIVE_ACTIVITY_SUBSCRIBER_NOT_FOUND_MESSAGE
          )

      if (!isAlreadyUnsubscribed) throw exception

      logDebug("Live Activity subscription is already unregistered")
    }
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
      url = "${config.apiUrl}/statistics/v1/$platform/projects/${config.projectId}/live-notifications/$liveNotificationId/events",
      token = config.apiKey,
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
    java.text
      .SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
      .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
      .format(java.util.Date())

  /**
   * Fetches the current live notification document (configuration + live data +
   * lifecycle) as raw JSON, used to catch up a subscriber that joined after the
   * `start` push was already delivered. Returns null on any failure.
   */
  suspend fun fetchLiveActivity(liveNotificationId: String): String? =
    runCatching {
      apiService
        .getLiveActivity(
          url = "${config.apiUrl}/core/projects/${config.projectId}/live-notifications/$liveNotificationId",
          token = config.apiKey,
        ).string()
    }.onFailure { logError("fetchLiveActivity($liveNotificationId) failed", it) }.getOrNull()

  private fun liveActivitySubscribersUrl(liveNotificationId: String): String =
    "${config.apiUrl}/core/projects/${config.projectId}/live-notifications/$liveNotificationId/subscribers"

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
          sdkVersion = PushNotifications.VERSION,
          osVersion = osVersion(),
        ),
      endpoint =
        LiveActivityEndpoint(
          transport = transport,
          pushToken = sharedPref.lastToken.orEmpty(),
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

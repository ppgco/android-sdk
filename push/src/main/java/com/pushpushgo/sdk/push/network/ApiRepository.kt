package com.pushpushgo.sdk.push.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pushpushgo.sdk.core.api.Config
import com.pushpushgo.sdk.push.PushNotifications
import com.pushpushgo.sdk.push.exception.PushPushException
import com.pushpushgo.sdk.push.exception.isAccessRevoked
import com.pushpushgo.sdk.push.exception.isSubscriberInvalid
import com.pushpushgo.sdk.push.network.data.InstallationMetadata
import com.pushpushgo.sdk.push.network.data.LiveActivityEndpoint
import com.pushpushgo.sdk.push.network.data.LiveActivityEndpointUpdateRequest
import com.pushpushgo.sdk.push.network.data.LiveActivityEventDto
import com.pushpushgo.sdk.push.network.data.LiveActivityEventsRequest
import com.pushpushgo.sdk.push.network.data.LiveActivitySubscribeRequest
import com.pushpushgo.sdk.push.network.data.TokenRequest
import com.pushpushgo.sdk.push.utils.PlatformType
import com.pushpushgo.sdk.push.utils.PushTokenProvider
import com.pushpushgo.sdk.push.utils.getPlatformType
import com.pushpushgo.sdk.push.utils.logDebug
import com.pushpushgo.sdk.push.utils.logError
import com.pushpushgo.sdk.push.utils.osVersion

internal class ApiRepository(
  private val apiService: ApiService,
  private val sharedPref: SharedPreferencesHelper,
  private val config: Config,
  private val pushTokenProvider: PushTokenProvider,
) {
  companion object {
    private const val HTTP_NOT_FOUND = 404
  }

  var onLiveActivityAccessRevoked: () -> Unit = {}

  suspend fun registerToken(token: String?) {
    logDebug("registerToken invoked: $token")

    val tokenToRegister = token ?: pushTokenProvider.getToken()

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
      logDebug("Unregister skipped - not registered")
      return
    }

    deleteSubscriber(subscriberId)

    sharedPref.subscriberId = ""
  }

  /**
   * Removes [subscriberId] from the configured project. A subscriber that is already inactive,
   * does not exist, or belongs to another project (so these credentials can never remove it)
   * counts as removed.
   */
  suspend fun deleteSubscriber(subscriberId: String) {
    try {
      apiService.unregisterSubscriber(
        token = config.apiKey,
        projectId = config.projectId,
        subscriberId = subscriberId,
      )
    } catch (exception: PushPushException) {
      if (!exception.isSubscriberInvalid()) throw exception

      logDebug("Subscriber is already unregistered")
    }
  }

  /**
   * Registers this device as a subscriber of the given live notification and
   * returns the backend-assigned LA subscriber id.
   */
  suspend fun subscribeToLiveActivity(liveNotificationId: String): String {
    val response =
      liveActivityCall {
        apiService.subscribeLiveActivity(
          url = liveActivitySubscribersUrl(liveNotificationId),
          token = config.apiKey,
          body = buildLiveActivitySubscribeRequest(),
        )
      }
    logDebug("subscribeToLiveActivity($liveNotificationId) -> ${response.id}")
    return response.id
  }

  /** Points an existing LA subscriber at [pushToken], e.g. after the push token changed. */
  suspend fun updateLiveActivityEndpoint(
    liveNotificationId: String,
    liveActivitySubscriberId: String,
    pushToken: String,
  ) {
    liveActivityCall {
      apiService.updateLiveActivitySubscriberEndpoint(
        url = "${liveActivitySubscribersUrl(liveNotificationId)}/$liveActivitySubscriberId/endpoint",
        token = config.apiKey,
        body =
          LiveActivityEndpointUpdateRequest(
            installationMetadata = installationMetadata(),
            endpoint = liveActivityEndpoint(pushToken),
          ),
      )
    }
  }

  /** Unregisters this device from the given live notification. */
  suspend fun unsubscribeFromLiveActivity(
    liveNotificationId: String,
    liveActivitySubscriberId: String,
  ) {
    try {
      liveActivityCall {
        apiService.unsubscribeLiveActivity(
          url = "${liveActivitySubscribersUrl(liveNotificationId)}/$liveActivitySubscriberId",
          token = config.apiKey,
        )
      }
    } catch (exception: PushPushException) {
      // The backend answers 404 when the live notification or the subscriber is already gone.
      if (exception.statusCode != HTTP_NOT_FOUND) throw exception

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
    liveActivityCall {
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
  }

  private fun isoTimestamp(): String =
    java.text
      .SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
      .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
      .format(java.util.Date())

  /**
   * Fetches the current live notification document (configuration + live data +
   * lifecycle) as raw JSON, used to catch up a subscriber that joined after the
   * `start` push was already delivered. Returns null on any failure, and when the
   * live notification is not running: the backend serves the document only then.
   */
  suspend fun fetchLiveActivity(liveNotificationId: String): String? =
    runCatching {
      liveActivityCall {
        apiService
          .getLiveActivity(
            url = liveNotificationUrl(liveNotificationId),
            token = config.apiKey,
          ).string()
      }
    }.onFailure {
      if ((it as? PushPushException)?.statusCode == HTTP_NOT_FOUND) {
        logDebug("Live notification $liveNotificationId is not running")
      } else {
        logError("fetchLiveActivity($liveNotificationId) failed", it)
      }
    }.getOrNull()

  private suspend fun <T> liveActivityCall(call: suspend () -> T): T =
    try {
      call()
    } catch (e: PushPushException) {
      if (e.isAccessRevoked()) onLiveActivityAccessRevoked()
      throw e
    }

  private fun liveNotificationUrl(liveNotificationId: String): String =
    "${config.apiUrl}/core/v1/android/projects/${config.projectId}/live-notifications/$liveNotificationId"

  private fun liveActivitySubscribersUrl(liveNotificationId: String): String = "${liveNotificationUrl(liveNotificationId)}/subscribers"

  private suspend fun buildLiveActivitySubscribeRequest(): LiveActivitySubscribeRequest =
    LiveActivitySubscribeRequest(
      installationId = sharedPref.installationId,
      installationMetadata = installationMetadata(),
      endpoint = liveActivityEndpoint(pushTokenProvider.getToken()),
    )

  private fun installationMetadata(): InstallationMetadata =
    InstallationMetadata(
      sdkVersion = PushNotifications.VERSION,
      osVersion = osVersion(),
    )

  private fun liveActivityEndpoint(pushToken: String): LiveActivityEndpoint {
    val transport =
      when (getPlatformType()) {
        PlatformType.FCM -> "FCM"
        PlatformType.HCM -> "HMS"
      }
    return LiveActivityEndpoint(transport = transport, pushToken = pushToken)
  }

  suspend fun getBitmapFromUrl(url: String?): Bitmap? {
    if (url.isNullOrBlank()) return null

    return BitmapFactory.decodeStream(
      apiService.getRawResponse(url).byteStream(),
    )
  }
}

package com.pushpushgo.sdk.push.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pushpushgo.sdk.core.api.Config
import com.pushpushgo.sdk.push.PushNotifications
import com.pushpushgo.sdk.push.data.Event
import com.pushpushgo.sdk.push.data.EventType
import com.pushpushgo.sdk.push.data.Payload
import com.pushpushgo.sdk.push.exception.PushPushException
import com.pushpushgo.sdk.push.network.ApiService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.network.data.TokenUpdateRequest
import com.pushpushgo.sdk.push.utils.getPlatformPushToken
import com.pushpushgo.sdk.push.utils.logDebug
import com.pushpushgo.sdk.push.utils.logError
import com.pushpushgo.sdk.push.utils.osVersion
import kotlinx.coroutines.coroutineScope

internal class UploadWorker(
  private val context: Context,
  parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
  companion object {
    const val TYPE = "type"

    const val EVENT = "event"
    const val SYNC_TOKEN = "sync_token"
    const val SYNC_TOKEN_PERIODIC = "sync_token_periodic"
    private const val MAX_EVENT_ATTEMPTS = 3
    private const val MAX_SYNC_TOKEN_ATTEMPTS = 3

    const val WORK_PROJECT_ID = "work:project_id"
    const val WORK_API_KEY = "work:api_key"
    const val WORK_API_URL = "work:api_url"

    const val SYNC_TOKEN_TOKEN = "sync_token:token"
    const val SYNC_TOKEN_SUBSCRIBER_ID = "sync_token:subscriber_id"

    // EVENT payload keys
    const val EVENT_TYPE = "event_type"
    const val EVENT_BUTTON_ID = "event_button_id"
    const val EVENT_CAMPAIGN = "event_campaign"
    const val EVENT_SUBSCRIBER_ID = "event_subscriber_id"
  }

  private val sharedPreferencesHelper = SharedPreferencesHelper(context)

  override suspend fun doWork(): Result =
    coroutineScope {
      logDebug("UploadWorker: started")

      val projectId = inputData.getString(WORK_PROJECT_ID)
      val apiKey = inputData.getString(WORK_API_KEY)
      val apiUrl = inputData.getString(WORK_API_URL)

      val config: Config =
        if (projectId != null && apiKey != null && apiUrl != null) {
          Config.create(projectId, apiKey, apiUrl)
        } else {
          logDebug("UploadWorker: Work not configured with project credentials, skipping.")

          return@coroutineScope Result.failure()
        }

      val apiService = ApiService.fromConfig(config)

      val type = inputData.getString(TYPE)

      try {
        when (type) {
          EVENT ->
            sendEvent(
              config = config,
              apiService = apiService,
              type = EventType.valueOf(inputData.getString(EVENT_TYPE).orEmpty()),
              buttonId = inputData.getInt(EVENT_BUTTON_ID, 0),
              campaign = inputData.getString(EVENT_CAMPAIGN).orEmpty(),
              subscriberId = inputData.getString(EVENT_SUBSCRIBER_ID),
            )
          SYNC_TOKEN ->
            syncToken(
              config = config,
              apiService = apiService,
              subscriberId = inputData.getString(SYNC_TOKEN_SUBSCRIBER_ID).orEmpty(),
              pushToken = inputData.getString(SYNC_TOKEN_TOKEN),
            )
          SYNC_TOKEN_PERIODIC ->
            syncToken(
              config = config,
              apiService = apiService,
              subscriberId = inputData.getString(SYNC_TOKEN_SUBSCRIBER_ID).orEmpty(),
              pushToken = null,
            )
          else -> return@coroutineScope Result.failure()
        }
      } catch (e: Throwable) {
        logError("UploadWorker error", e)

        return@coroutineScope when {
          "Please configure FCM keys and senderIds on your " in e.message.orEmpty() -> Result.failure()
          type == EVENT && !shouldRetry(e, MAX_EVENT_ATTEMPTS) -> Result.failure()
          type in setOf(SYNC_TOKEN, SYNC_TOKEN_PERIODIC) && !shouldRetry(e, MAX_SYNC_TOKEN_ATTEMPTS) -> Result.failure()
          type in setOf(EVENT, SYNC_TOKEN, SYNC_TOKEN_PERIODIC) -> Result.retry()
          else -> Result.failure()
        }
      }

      logDebug("UploadWorker: success")

      Result.success()
    }

  private fun shouldRetry(
    throwable: Throwable,
    maxAttempts: Int,
  ): Boolean {
    if (runAttemptCount >= maxAttempts - 1) return false

    val statusCode = (throwable as? PushPushException)?.statusCode
    return statusCode == null || statusCode == 429 || statusCode >= 500
  }

  private suspend fun sendEvent(
    config: Config,
    apiService: ApiService,
    type: EventType,
    buttonId: Int,
    campaign: String,
    subscriberId: String?,
  ) {
    if (subscriberId.isNullOrBlank()) {
      return logError("Cannot send event - empty subscriberId")
    }

    apiService.sendEvent(
      token = config.apiKey,
      projectId = config.projectId,
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

  private suspend fun syncToken(
    config: Config,
    apiService: ApiService,
    subscriberId: String,
    pushToken: String?,
  ) {
    if (subscriberId.isBlank()) {
      return logError("Cannot sync token - empty subscriberId")
    }

    val token = pushToken ?: getPlatformPushToken(context)

    if (token.isBlank()) {
      return logError("Cannot sync token - empty subscriberId")
    }

    if (token == sharedPreferencesHelper.lastToken) {
      return logDebug("Token sync skipped - token unchanged")
    }

    apiService.updateSubscriberToken(
      token = config.apiKey,
      projectId = config.projectId,
      subscriberId = subscriberId,
      body =
        TokenUpdateRequest(
          token = token,
          sdkVersion = PushNotifications.VERSION,
          osVersion = osVersion(),
          installationId = sharedPreferencesHelper.installationId,
        ),
    )

    sharedPreferencesHelper.onPushTokenUpdated(subscriberId, token)
  }
}

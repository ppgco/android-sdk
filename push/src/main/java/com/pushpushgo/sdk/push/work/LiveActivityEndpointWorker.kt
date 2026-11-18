package com.pushpushgo.sdk.push.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pushpushgo.sdk.core.api.Config
import com.pushpushgo.sdk.push.exception.isTransientApiError
import com.pushpushgo.sdk.push.network.ApiRepository
import com.pushpushgo.sdk.push.network.DefaultApiServiceFactory
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.utils.PlatformPushTokenProvider
import com.pushpushgo.sdk.push.utils.isSameProjectAs
import com.pushpushgo.sdk.push.utils.logDebug
import com.pushpushgo.sdk.push.utils.logError
import kotlinx.coroutines.CancellationException

/**
 * Points every Live Activity subscription of the current project at a new push token, so Live
 * Activity pushes keep arriving after the token changes.
 *
 * A subscription the API rejects (e.g. of a live notification that already ended) is skipped. A
 * transient error retries the whole update, which the API accepts repeatedly.
 */
internal class LiveActivityEndpointWorker(
  context: Context,
  parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
  companion object {
    const val PROJECT_ID = "live_activity_endpoint:project_id"
    const val API_KEY = "live_activity_endpoint:api_key"
    const val API_URL = "live_activity_endpoint:api_url"
    const val PUSH_TOKEN = "live_activity_endpoint:push_token"

    private const val MAX_ATTEMPTS = 10
  }

  private val sharedPreferencesHelper = SharedPreferencesHelper(context)

  override suspend fun doWork(): Result {
    val config = readConfig() ?: return Result.failure()
    val pushToken = inputData.getString(PUSH_TOKEN)?.takeIf { it.isNotBlank() } ?: return Result.failure()

    if (sharedPreferencesHelper.projectOwner?.isSameProjectAs(config) != true) {
      logDebug("Live Activity subscriptions belong to another project now, skipping their endpoint update")
      return Result.success()
    }

    val apiRepository =
      ApiRepository(DefaultApiServiceFactory.create(config), sharedPreferencesHelper, config, PlatformPushTokenProvider(applicationContext))

    var shouldRetry = false
    for ((liveNotificationId, liveActivitySubscriberId) in sharedPreferencesHelper.getLiveActivitySubscriptions()) {
      try {
        apiRepository.updateLiveActivityEndpoint(liveNotificationId, liveActivitySubscriberId, pushToken)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        logError("Live Activity $liveNotificationId endpoint update failed", e)
        if (e.isTransientApiError()) shouldRetry = true
      }
    }

    return if (shouldRetry && runAttemptCount < MAX_ATTEMPTS - 1) Result.retry() else Result.success()
  }

  private fun readConfig(): Config? {
    val projectId = inputData.getString(PROJECT_ID)
    val apiKey = inputData.getString(API_KEY)
    val apiUrl = inputData.getString(API_URL)

    if (projectId == null || apiKey == null || apiUrl == null) {
      logError("Live Activity endpoint update is not configured with project credentials, skipping")
      return null
    }

    return Config.create(projectId, apiKey, apiUrl)
  }
}

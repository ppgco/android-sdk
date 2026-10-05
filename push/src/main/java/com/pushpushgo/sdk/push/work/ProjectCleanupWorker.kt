package com.pushpushgo.sdk.push.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pushpushgo.sdk.core.api.Config
import com.pushpushgo.sdk.push.exception.isTransientApiError
import com.pushpushgo.sdk.push.network.ApiRepository
import com.pushpushgo.sdk.push.network.ApiService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.utils.isSameProjectAs
import com.pushpushgo.sdk.push.utils.logDebug
import com.pushpushgo.sdk.push.utils.logError
import kotlinx.coroutines.CancellationException

/**
 * Removes the subscriber and Live Activity subscriptions this device had in a project the SDK no
 * longer works with. The project's credentials travel in the work input, so the removal finishes
 * even after the SDK moved to another project or the app was restarted.
 *
 * The backend hands out the same subscriber again when the device registers with the same push
 * token while that subscriber is still active. If the SDK went back to the project before this
 * work ran, the subscriber is in use again and is left alone.
 */
internal class ProjectCleanupWorker(
  context: Context,
  parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
  companion object {
    const val PROJECT_ID = "project_cleanup:project_id"
    const val API_KEY = "project_cleanup:api_key"
    const val API_URL = "project_cleanup:api_url"
    const val SUBSCRIBER_ID = "project_cleanup:subscriber_id"
    const val LIVE_ACTIVITY_IDS = "project_cleanup:live_activity_ids"
    const val LIVE_ACTIVITY_SUBSCRIBER_IDS = "project_cleanup:live_activity_subscriber_ids"

    // With exponential backoff capped by WorkManager at 5 hours this keeps retrying for ~4 days
    private const val MAX_ATTEMPTS = 30
  }

  private val sharedPreferencesHelper = SharedPreferencesHelper(context)

  override suspend fun doWork(): Result {
    val config = readConfig() ?: return Result.failure()
    val apiRepository = ApiRepository(applicationContext, ApiService.fromConfig(config), sharedPreferencesHelper, config)

    return try {
      inputData.getString(SUBSCRIBER_ID)?.let { removeSubscriber(config, apiRepository, it) }
      readLiveActivitySubscriptions().forEach { (liveActivityId, liveActivitySubscriberId) ->
        removeLiveActivitySubscription(config, apiRepository, liveActivityId, liveActivitySubscriberId)
      }

      logDebug("Project ${config.projectId} cleanup finished")
      Result.success()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      logError("Project ${config.projectId} cleanup failed", e)

      if (e.isTransientApiError() && runAttemptCount < MAX_ATTEMPTS - 1) Result.retry() else Result.failure()
    }
  }

  private suspend fun removeSubscriber(
    config: Config,
    apiRepository: ApiRepository,
    subscriberId: String,
  ) {
    if (isCurrentProject(config) && sharedPreferencesHelper.subscriberId == subscriberId) {
      return logDebug("Subscriber $subscriberId is in use again, skipping its removal")
    }

    apiRepository.deleteSubscriber(subscriberId)
  }

  private suspend fun removeLiveActivitySubscription(
    config: Config,
    apiRepository: ApiRepository,
    liveActivityId: String,
    liveActivitySubscriberId: String,
  ) {
    if (isCurrentProject(config) &&
      sharedPreferencesHelper.getLiveActivitySubscriberId(liveActivityId) == liveActivitySubscriberId
    ) {
      return logDebug("Live Activity subscription $liveActivitySubscriberId is in use again, skipping its removal")
    }

    apiRepository.unsubscribeFromLiveActivity(liveActivityId, liveActivitySubscriberId)
  }

  private fun isCurrentProject(config: Config): Boolean = sharedPreferencesHelper.projectOwner?.isSameProjectAs(config) == true

  private fun readConfig(): Config? {
    val projectId = inputData.getString(PROJECT_ID)
    val apiKey = inputData.getString(API_KEY)
    val apiUrl = inputData.getString(API_URL)

    if (projectId == null || apiKey == null || apiUrl == null) {
      logError("Project cleanup is not configured with project credentials, skipping")
      return null
    }

    return Config.create(projectId, apiKey, apiUrl)
  }

  private fun readLiveActivitySubscriptions(): List<Pair<String, String>> {
    val liveActivityIds = inputData.getStringArray(LIVE_ACTIVITY_IDS).orEmpty()
    val liveActivitySubscriberIds = inputData.getStringArray(LIVE_ACTIVITY_SUBSCRIBER_IDS).orEmpty()

    return liveActivityIds.zip(liveActivitySubscriberIds)
  }
}

package com.pushpushgo.sdk.push.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.pushpushgo.sdk.core.api.Config
import com.pushpushgo.sdk.push.data.EventType
import com.pushpushgo.sdk.push.utils.logDebug
import com.pushpushgo.sdk.push.work.UploadWorker.Companion.EVENT
import com.pushpushgo.sdk.push.work.UploadWorker.Companion.EVENT_BUTTON_ID
import com.pushpushgo.sdk.push.work.UploadWorker.Companion.EVENT_CAMPAIGN
import com.pushpushgo.sdk.push.work.UploadWorker.Companion.EVENT_SUBSCRIBER_ID
import com.pushpushgo.sdk.push.work.UploadWorker.Companion.EVENT_TYPE
import com.pushpushgo.sdk.push.work.UploadWorker.Companion.SYNC_TOKEN
import com.pushpushgo.sdk.push.work.UploadWorker.Companion.SYNC_TOKEN_PERIODIC
import com.pushpushgo.sdk.push.work.UploadWorker.Companion.SYNC_TOKEN_SUBSCRIBER_ID
import com.pushpushgo.sdk.push.work.UploadWorker.Companion.SYNC_TOKEN_TOKEN
import com.pushpushgo.sdk.push.work.UploadWorker.Companion.TYPE
import com.pushpushgo.sdk.push.work.UploadWorker.Companion.WORK_API_KEY
import com.pushpushgo.sdk.push.work.UploadWorker.Companion.WORK_API_URL
import com.pushpushgo.sdk.push.work.UploadWorker.Companion.WORK_PROJECT_ID
import java.util.concurrent.TimeUnit

internal class UploadManager(
  context: Context,
  private val config: Config,
) {
  companion object {
    private const val UPLOAD_RETRY_DELAY = 30L
    private const val TOKEN_SYNC_PERIOD_DAYS = 14L

    internal const val SYNC_TOKEN_WORK_NAME = "com.pushpushgo.sdk.push.work:sync-token"
    internal const val PERIODIC_TOKEN_SYNC_WORK_NAME = "com.pushpushgo.sdk.push.work:sync-token-periodic"
  }

  private val workManager = WorkManager.getInstance(context)

  private val networkConstraints =
    Constraints
      .Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .build()

  fun syncToken(
    subscriberId: String,
    token: String?,
  ) {
    if (subscriberId.isBlank()) {
      return logDebug("Token sync not enqueued. Reason: empty subscriberId")
    }

    workManager.enqueueUniqueWork(
      SYNC_TOKEN_WORK_NAME,
      ExistingWorkPolicy.REPLACE,
      OneTimeWorkRequestBuilder<UploadWorker>()
        .setInputData(
          workDataOf(
            TYPE to SYNC_TOKEN,
            WORK_PROJECT_ID to config.projectId,
            WORK_API_KEY to config.apiKey,
            WORK_API_URL to config.apiUrl,
            SYNC_TOKEN_SUBSCRIBER_ID to subscriberId,
            SYNC_TOKEN_TOKEN to token,
          ),
        ).setBackoffCriteria(BackoffPolicy.LINEAR, UPLOAD_RETRY_DELAY, TimeUnit.SECONDS)
        .setConstraints(networkConstraints)
        .build(),
    )
  }

  fun schedulePeriodicTokenSync(subscriberId: String) {
    if (subscriberId.isBlank()) {
      return logDebug("Periodic token sync not scheduled. Reason: empty subscriberId")
    }

    workManager.enqueueUniquePeriodicWork(
      PERIODIC_TOKEN_SYNC_WORK_NAME,
      ExistingPeriodicWorkPolicy.UPDATE,
      PeriodicWorkRequestBuilder<UploadWorker>(
        TOKEN_SYNC_PERIOD_DAYS,
        TimeUnit.DAYS,
      ).setInitialDelay(
        TOKEN_SYNC_PERIOD_DAYS,
        TimeUnit.DAYS,
      ).setInputData(
        workDataOf(
          TYPE to SYNC_TOKEN_PERIODIC,
          WORK_PROJECT_ID to config.projectId,
          WORK_API_KEY to config.apiKey,
          WORK_API_URL to config.apiUrl,
          SYNC_TOKEN_SUBSCRIBER_ID to subscriberId,
        ),
      ).setBackoffCriteria(BackoffPolicy.LINEAR, UPLOAD_RETRY_DELAY, TimeUnit.SECONDS)
        .setConstraints(networkConstraints)
        .build(),
    )
  }

  /**
   * Enqueues a delivery/click event for durable, retried upload. Events are
   * fire-and-forget at the call site but survive process death and transient
   * network loss because they run as a [UploadWorker] job with exponential
   * backoff.
   *
   * Not unique work: events are never de-duplicated against one another.
   */
  fun sendEvent(
    type: EventType,
    buttonId: Int,
    campaign: String,
    subscriberId: String?,
  ) {
    logDebug("Event enqueued: ${type.value}")

    workManager.enqueue(
      OneTimeWorkRequestBuilder<UploadWorker>()
        .setInputData(
          workDataOf(
            WORK_PROJECT_ID to config.projectId,
            WORK_API_KEY to config.apiKey,
            WORK_API_URL to config.apiUrl,
            TYPE to EVENT,
            EVENT_TYPE to type.name,
            EVENT_BUTTON_ID to buttonId,
            EVENT_CAMPAIGN to campaign,
            EVENT_SUBSCRIBER_ID to subscriberId,
          ),
        ).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, UPLOAD_RETRY_DELAY, TimeUnit.SECONDS)
        .setConstraints(networkConstraints)
        .build(),
    )
  }

  fun cancelAllJobs() {
    workManager.cancelUniqueWork(SYNC_TOKEN_WORK_NAME)
    workManager.cancelUniqueWork(PERIODIC_TOKEN_SYNC_WORK_NAME)
  }
}

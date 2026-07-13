package com.pushpushgo.sdk.push.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.pushpushgo.sdk.push.data.EventType
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.utils.logDebug
import com.pushpushgo.sdk.push.work.UploadWorker.Companion.DATA
import com.pushpushgo.sdk.push.work.UploadWorker.Companion.EVENT
import com.pushpushgo.sdk.push.work.UploadWorker.Companion.EVENT_BUTTON_ID
import com.pushpushgo.sdk.push.work.UploadWorker.Companion.EVENT_CAMPAIGN
import com.pushpushgo.sdk.push.work.UploadWorker.Companion.EVENT_PROJECT_ID
import com.pushpushgo.sdk.push.work.UploadWorker.Companion.EVENT_SUBSCRIBER_ID
import com.pushpushgo.sdk.push.work.UploadWorker.Companion.EVENT_TYPE
import com.pushpushgo.sdk.push.work.UploadWorker.Companion.REGISTER
import com.pushpushgo.sdk.push.work.UploadWorker.Companion.SYNC_TOKEN
import com.pushpushgo.sdk.push.work.UploadWorker.Companion.SYNC_TOKEN_PERIODIC
import com.pushpushgo.sdk.push.work.UploadWorker.Companion.TYPE
import com.pushpushgo.sdk.push.work.UploadWorker.Companion.UNREGISTER
import java.util.concurrent.TimeUnit

internal class UploadManager(
  context: Context,
  private val sharedPref: SharedPreferencesHelper,
) {
  companion object {
    private const val UPLOAD_DELAY = 10L
    private const val UPLOAD_RETRY_DELAY = 30L
    private const val TOKEN_SYNC_PERIOD_DAYS = 14L
  }

  private val workManager = WorkManager.getInstance(context)

  private val networkConstraints =
    Constraints
      .Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .build()

  fun sendRegister(token: String?) {
    logDebug("Register enqueued")

    enqueueJob(REGISTER, isMustRunImmediately = true, data = token)
    listOf(UNREGISTER).forEach {
      workManager.cancelUniqueWork(it)
    }
  }

  fun sendUnregister() {
    if (!sharedPref.isSubscribed) {
      logDebug("Can't unregister, because device not registered. Skipping")
      return
    }

    logDebug("Unregister enqueued")

    enqueueJob(UNREGISTER, isMustRunImmediately = true)
    listOf(REGISTER, SYNC_TOKEN).forEach {
      workManager.cancelUniqueWork(it)
    }
  }

  fun syncToken(token: String?) {
    logDebug("Token sync enqueued")

    enqueueJob(SYNC_TOKEN, isMustRunImmediately = true, data = token)
  }

  fun schedulePeriodicTokenSync() {
    if (!sharedPref.isSubscribed) {
      return logDebug("Periodic token sync not scheduled. Reason: not subscribed")
    }

    workManager.enqueueUniquePeriodicWork(
      SYNC_TOKEN_PERIODIC,
      ExistingPeriodicWorkPolicy.KEEP,
      PeriodicWorkRequestBuilder<UploadWorker>(TOKEN_SYNC_PERIOD_DAYS, TimeUnit.DAYS)
        .setInputData(workDataOf(TYPE to SYNC_TOKEN_PERIODIC))
        .setBackoffCriteria(BackoffPolicy.LINEAR, UPLOAD_RETRY_DELAY, TimeUnit.SECONDS)
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
    projectId: String?,
    subscriberId: String?,
  ) {
    logDebug("Event enqueued: ${type.value}")

    workManager.enqueue(
      OneTimeWorkRequestBuilder<UploadWorker>()
        .setInputData(
          workDataOf(
            TYPE to EVENT,
            EVENT_TYPE to type.name,
            EVENT_BUTTON_ID to buttonId,
            EVENT_CAMPAIGN to campaign,
            EVENT_PROJECT_ID to projectId,
            EVENT_SUBSCRIBER_ID to subscriberId,
          ),
        ).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, UPLOAD_RETRY_DELAY, TimeUnit.SECONDS)
        .setConstraints(networkConstraints)
        .build(),
    )
  }

  fun cancelAllJobs() {
    workManager.cancelUniqueWork(REGISTER)
    workManager.cancelUniqueWork(UNREGISTER)
    workManager.cancelUniqueWork(SYNC_TOKEN)
    workManager.cancelUniqueWork(SYNC_TOKEN_PERIODIC)
  }

  fun cancelPeriodicTokenSync() {
    workManager.cancelUniqueWork(SYNC_TOKEN_PERIODIC)
  }

  private fun enqueueJob(
    name: String,
    data: String? = null,
    isMustRunImmediately: Boolean = false,
  ) {
    workManager.enqueueUniqueWork(
      name,
      if (name == REGISTER || name == SYNC_TOKEN) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
      OneTimeWorkRequestBuilder<UploadWorker>()
        .setInputData(
          workDataOf(TYPE to name, DATA to data),
        ).setBackoffCriteria(BackoffPolicy.LINEAR, UPLOAD_RETRY_DELAY, TimeUnit.SECONDS)
        .setInitialDelay(if (isMustRunImmediately || isJobAlreadyEnqueued(name)) 0 else UPLOAD_DELAY, TimeUnit.SECONDS)
        .setConstraints(networkConstraints)
        .build(),
    )
  }

  private fun isJobAlreadyEnqueued(name: String) =
    try {
      workManager.getWorkInfosForUniqueWork(name).get().any {
        it.state == WorkInfo.State.ENQUEUED
      }
    } catch (e: InterruptedException) {
      false
    }
}

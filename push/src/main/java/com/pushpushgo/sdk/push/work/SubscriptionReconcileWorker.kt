package com.pushpushgo.sdk.push.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pushpushgo.sdk.push.PushNotifications
import com.pushpushgo.sdk.push.exception.isTransientApiError
import com.pushpushgo.sdk.push.utils.logError
import kotlinx.coroutines.CancellationException

/**
 * Retries the push subscription reconciliation that failed because of the network or the API. It
 * reconciles the runtime that is current when it runs, so it carries no project credentials.
 */
internal class SubscriptionReconcileWorker(
  context: Context,
  parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
  companion object {
    private const val MAX_ATTEMPTS = 30
  }

  override suspend fun doWork(): Result =
    try {
      PushNotifications.reconcileSubscription()
      Result.success()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      logError("Subscription reconciliation failed", e)

      if (e.isTransientApiError() && runAttemptCount < MAX_ATTEMPTS - 1) Result.retry() else Result.failure()
    }
}

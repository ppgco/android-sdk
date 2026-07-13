package com.pushpushgo.sdk.push.subscription

import com.pushpushgo.sdk.push.network.ApiRepository
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.utils.logError
import com.pushpushgo.sdk.push.work.UploadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single owner of the subscribe/unsubscribe lifecycle. Both the durable,
 * fire-and-forget path ([subscribe]/[unsubscribe], enqueued through WorkManager
 * with retry/backoff) and the immediate, awaitable path ([subscribeNow]/
 * [unsubscribeNow], a direct network call that surfaces failures to the caller)
 * funnel through one [guard] so the migration check, locking and `isSubscribed`
 * bookkeeping live in exactly one place.
 *
 * The two mechanisms are kept on purpose: callers that just want best-effort
 * registration use the durable path; callers that need confirmation/error
 * propagation use the immediate path.
 */
internal class SubscriptionController(
  private val scope: CoroutineScope,
  private val mutex: Mutex,
  private val apiRepository: ApiRepository,
  private val uploadManager: UploadManager,
  private val sharedPref: SharedPreferencesHelper,
  private val isMigrating: AtomicBoolean,
  private val notificationsEnabled: () -> Boolean,
) {
  fun subscribe() {
    if (!notificationsEnabled()) {
      return logError("Notifications disabled! Subscriber registration canceled")
    }

    scope.launch {
      guard(throwOnMigration = false) {
        sharedPref.isSubscribed = true
        uploadManager.sendRegister(null)
        uploadManager.schedulePeriodicTokenSync()
      }
    }
  }

  fun unsubscribe() {
    scope.launch {
      guard(throwOnMigration = false) {
        uploadManager.sendUnregister()
        uploadManager.cancelPeriodicTokenSync()
        sharedPref.isSubscribed = false
      }
    }
  }

  suspend fun subscribeNow() {
    check(notificationsEnabled()) {
      "Notifications disabled! Subscriber registration canceled"
    }

    withContext(Dispatchers.IO) {
      guard(throwOnMigration = true) {
        apiRepository.registerToken(null)
        sharedPref.isSubscribed = true
        uploadManager.schedulePeriodicTokenSync()
      }
    }
  }

  suspend fun unsubscribeNow() {
    withContext(Dispatchers.IO) {
      guard(throwOnMigration = true) {
        apiRepository.unregisterSubscriber()
        uploadManager.cancelPeriodicTokenSync()
        sharedPref.isSubscribed = false
      }
    }
  }

  private suspend fun guard(
    throwOnMigration: Boolean,
    block: suspend () -> Unit,
  ) {
    mutex.withLock {
      if (isMigrating.get()) {
        check(!throwOnMigration) { "Migration in progress" }
        return@withLock logError("Migration in progress")
      }
      block()
    }
  }
}

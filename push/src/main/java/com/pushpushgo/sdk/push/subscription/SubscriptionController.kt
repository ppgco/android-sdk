package com.pushpushgo.sdk.push.subscription

import com.pushpushgo.sdk.push.network.ApiRepository
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.work.UploadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class SubscriptionController(
  private val mutex: Mutex,
  private val apiRepository: ApiRepository,
  private val uploadManager: UploadManager,
  private val sharedPref: SharedPreferencesHelper,
  private val notificationsEnabled: () -> Boolean,
) {
  suspend fun subscribe() {
    check(notificationsEnabled()) {
      "Notifications disabled! Subscriber registration canceled"
    }

    withContext(Dispatchers.IO) {
      mutex.withLock {
        apiRepository.registerToken(null)
        sharedPref.isSubscribed = true
        uploadManager.schedulePeriodicTokenSync()
      }
    }
  }

  suspend fun unsubscribe() {
    withContext(Dispatchers.IO) {
      mutex.withLock {
        apiRepository.unregisterSubscriber()
        uploadManager.cancelPeriodicTokenSync()
        sharedPref.isSubscribed = false
      }
    }
  }
}

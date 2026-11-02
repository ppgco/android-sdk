package com.pushpushgo.sdk.core.api

import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

abstract class PushSubscriptionProviderJavaAdapter : PushSubscriptionProvider {
  /**
   * Requests push notifications for the user.
   *
   * This updates the user's preference to receive notifications and is idempotent.
   */
  abstract fun subscribeAsync(): CompletableFuture<Void?>

  /**
   * Stops requesting push notifications for the user.
   *
   * This updates the user's preference not to receive notifications and is idempotent.
   */
  abstract fun unsubscribeAsync(): CompletableFuture<Void?>

  override suspend fun subscribe() {
    subscribeAsync().await()
  }

  override suspend fun unsubscribe() {
    unsubscribeAsync().await()
  }
}

private suspend fun <T> CompletableFuture<T>.await(): T =
  suspendCancellableCoroutine { cont ->
    whenComplete { result, throwable ->
      if (throwable != null) {
        cont.resumeWithException(throwable)
      } else {
        cont.resume(result)
      }
    }

    cont.invokeOnCancellation {
      cancel(true)
    }
  }

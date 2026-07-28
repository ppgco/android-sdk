package com.pushpushgo.sdk.core.api

import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

abstract class PushSubscriptionProviderJavaAdapter : PushSubscriptionProvider {
  /**
   * Subscribes to the underlying push provider.
   * Must complete when registration finishes and be idempotent.
   */
  abstract fun subscribeFuture(): CompletableFuture<Void?>

  /**
   * Unsubscribes from the underlying push provider.
   * Must complete when unregistration finishes and be idempotent.
   */
  abstract fun unsubscribeFuture(): CompletableFuture<Void?>

  override suspend fun subscribe() {
    subscribeFuture().await()
  }

  override suspend fun unsubscribe() {
    unsubscribeFuture().await()
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

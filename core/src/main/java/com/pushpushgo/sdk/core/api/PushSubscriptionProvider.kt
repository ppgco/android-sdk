package com.pushpushgo.sdk.core.api

interface PushSubscriptionProvider {
  /**
   * Subscribes to the underlying push provider.
   * Must complete when registration finishes and be idempotent.
   *
   * A Java adapter is provided via [PushSubscriptionProviderJavaAdapter.subscribeFuture].
   */
  suspend fun subscribe()

  /**
   * Unsubscribes from the underlying push provider.
   * Must complete when unregistration finishes and be idempotent.
   *
   * A Java adapter is provided via [PushSubscriptionProviderJavaAdapter.unsubscribeFuture].
   */
  suspend fun unsubscribe()

  fun isSubscribed(): Boolean

  fun getPushToken(): String?

  /**
   * Returns whether the notification channel used by underlying provider is enabled.
   */
  fun isNotificationChannelEnabled(): Boolean
}

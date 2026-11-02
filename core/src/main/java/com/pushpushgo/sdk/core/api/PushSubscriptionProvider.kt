package com.pushpushgo.sdk.core.api

interface PushSubscriptionProvider {
  /**
   * Requests push notifications for the user.
   *
   * This updates the user's preference to receive notifications and is idempotent.
   *
   * A Java adapter is provided via [PushSubscriptionProviderJavaAdapter.subscribeAsync].
   */
  suspend fun subscribe()

  /**
   * Stops requesting push notifications for the user.
   *
   * This updates the user's preference not to receive notifications and is idempotent.
   *
   * A Java adapter is provided via [PushSubscriptionProviderJavaAdapter.unsubscribeAsync].
   */
  suspend fun unsubscribe()

  /** Returns whether the user has requested push notifications. */
  fun isSubscriptionRequested(): Boolean

  /** Returns whether the user is currently subscribed to push notifications. */
  fun isSubscribed(): Boolean

  /** Returns the push token for the user's device, if available. */
  fun getPushToken(): String?

  /** Returns whether the notification channel for push notifications is enabled. */
  fun isNotificationChannelEnabled(): Boolean
}

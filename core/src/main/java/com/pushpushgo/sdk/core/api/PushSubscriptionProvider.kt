package com.pushpushgo.sdk.core.api

interface PushSubscriptionProvider {
  /**
   * Requests push notifications for the user.
   *
   * This stores the user's preference to receive notifications right away and is idempotent. The
   * device is registered in the background, so [isSubscribed] may still return `false` right after
   * this call.
   *
   * @throws IllegalStateException if notifications are disabled.
   */
  fun subscribe()

  /**
   * Stops requesting push notifications for the user.
   *
   * This stores the user's preference not to receive notifications right away and is idempotent.
   */
  fun unsubscribe()

  /** Returns whether the user has requested push notifications. */
  fun isSubscriptionRequested(): Boolean

  /** Returns whether the user is currently subscribed to push notifications. */
  fun isSubscribed(): Boolean

  /** Returns the push token for the user's device, if available. */
  fun getPushToken(): String?

  /** Returns whether the notification channel for push notifications is enabled. */
  fun isNotificationChannelEnabled(): Boolean
}

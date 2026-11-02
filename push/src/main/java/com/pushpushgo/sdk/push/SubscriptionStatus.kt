package com.pushpushgo.sdk.push

/**
 * Confirmed backend subscription status of the device in the current project.
 *
 * @see PushNotifications.getSubscriptionStatus
 * @see PushNotifications.subscriptionStatusFlow
 */
sealed interface SubscriptionStatus {
  /** The SDK has no confirmed active subscription in the current project. */
  data object Unsubscribed : SubscriptionStatus

  /** The backend has confirmed an active subscription with [subscriberId] in [projectId]. */
  data class Subscribed(
    val subscriberId: String,
    val projectId: String,
  ) : SubscriptionStatus
}

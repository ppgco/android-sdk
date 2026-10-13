package com.pushpushgo.sdk.push

/**
 * Push subscription status of the device in the current project.
 *
 * @see PushNotifications.getSubscriptionStatus
 * @see PushNotifications.subscriptionStatusFlow
 */
enum class SubscriptionStatus {
  /** The user did not subscribe, or [PushNotifications.unsubscribe] was called. */
  UNSUBSCRIBED,

  /** The device is registered in the current project and notifications are enabled. */
  SUBSCRIBED,

  /**
   * The user subscribed, but the notification permission or channel is disabled. The device is
   * registered again once notifications are enabled.
   */
  NOTIFICATIONS_DISABLED,

  /**
   * The user subscribed and notifications are enabled, but the device is not registered yet - e.g.
   * right after [PushNotifications.switchProject] or while the API is not reachable. The SDK keeps
   * registering it.
   */
  PENDING,
}

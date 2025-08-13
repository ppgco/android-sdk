package com.pushpushgo.inappmessages.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.pushpushgo.inappmessages.model.UserAudienceType

/**
 * Utility class for accessing push notification subscription state
 * from the PushPushGo SDK's SharedPreferences.
 *
 * This provides a bridge to the push notification SDK without creating a direct dependency.
 */
internal class PushNotificationStatusProvider(
  private val context: Context,
) {
  companion object {
    // These constants match the ones in PushPushGo SDK's SharedPreferencesHelper
    private const val IS_SUBSCRIBED = "_PushPushGoSDK_is_subscribed_"
    private const val ARE_NOTIFICATIONS_BLOCKED = "_PushPushGoSDK_notifications_blocked_"
  }

  private val sharedPreferences: SharedPreferences by lazy {
    PreferenceManager.getDefaultSharedPreferences(context)
  }

  /**
   * Checks if the user is currently subscribed to push notifications
   * by reading directly from the PushPushGo SDK's SharedPreferences
   *
   * @return true if subscribed (defaults to false if not found)
   */
  fun isSubscribed(): Boolean {
    val result = sharedPreferences.getBoolean(IS_SUBSCRIBED, false)
    return result
  }

  /**
   * Checks if notifications are blocked for the current user
   *
   * @return true if notifications are blocked (defaults to false if not found)
   */
  fun isNotificationsBlocked(): Boolean = sharedPreferences.getBoolean(ARE_NOTIFICATIONS_BLOCKED, false)

  /**
   * Utility method to check if a user matches the given audience type
   *
   * @param audienceType The audience type to check against
   * @return true if the current user matches the specified audience type
   */
  fun matchesAudienceType(audienceType: UserAudienceType): Boolean =
    when (audienceType) {
      UserAudienceType.ALL -> true
      UserAudienceType.SUBSCRIBER -> isSubscribed() && !isNotificationsBlocked()
      UserAudienceType.NON_SUBSCRIBER -> !isSubscribed() || isNotificationsBlocked()
      UserAudienceType.NOTIFICATIONS_BLOCKED -> isNotificationsBlocked()
    }
}

package com.pushpushgo.inappmessages.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.pushpushgo.inappmessages.model.UserAudienceType
import android.util.Log

/**
 * Utility class for accessing push notification subscription state
 * from the PushPushGo SDK's SharedPreferences.
 * 
 * This provides a bridge to the push notification SDK without creating a direct dependency.
 */
class PushNotificationStatusProvider(private val context: Context) {
    
    companion object {
        private const val TAG = "PushNotificationStatusProvider"
        
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
        Log.d(TAG, "Checking subscription status: $result")
        return result
    }
    
    /**
     * Checks if notifications are blocked for the current user
     * 
     * @return true if notifications are blocked (defaults to false if not found)
     */
    fun isNotificationsBlocked(): Boolean {
        return sharedPreferences.getBoolean(ARE_NOTIFICATIONS_BLOCKED, false)
    }
    
    /**
     * Utility method to check if a user matches the given audience type
     * 
     * @param audienceType The audience type to check against
     * @return true if the current user matches the specified audience type
     */
    fun matchesAudienceType(audienceType: UserAudienceType): Boolean {
        return when (audienceType) {
            UserAudienceType.ALL -> true
            UserAudienceType.SUBSCRIBER -> isSubscribed()
            UserAudienceType.NON_SUBSCRIBER -> !isSubscribed()
            UserAudienceType.NOTIFICATIONS_BLOCKED -> isNotificationsBlocked()
        }
    }
}

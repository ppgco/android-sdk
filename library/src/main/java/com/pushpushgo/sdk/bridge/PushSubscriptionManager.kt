package com.pushpushgo.sdk.bridge

import android.content.Context
import android.util.Log
import com.pushpushgo.sdk.PushPushGo

/**
 * Bridge interface that allows other components (in-app messages library)
 * to request push notification subscription without creating direct dependencies.
 */
interface PushSubscriptionBridgeManager {
    /**
     * Request user to subscribe to push notifications
     * 
     * @param context Android context
     * @return true if the subscription request was successfully initiated
     */
    fun requestSubscription(context: Context): Boolean
}

class PushPushGoSubscriptionBridgeManager : PushSubscriptionBridgeManager {
    companion object {
        private const val TAG = "PushSubscriptionManager"
    }

    override fun requestSubscription(context: Context): Boolean {
        try {
            if (!PushPushGo.isInitialized()) {
                Log.e(TAG, "PushPushGo SDK is not initialized")
                return false
            }

            val pushSdk = PushPushGo.getInstance()

            if (!pushSdk.areNotificationsEnabled()) {
                Log.e(TAG, "Notifications are not enabled")
                return false
            }

            pushSdk.registerSubscriber()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting subscription", e)
            return false
        }
    }
}
